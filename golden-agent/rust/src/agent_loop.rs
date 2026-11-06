//! Element 4, the loop. The application code around the model.
//!
//! Each pass: send the full history and tools, read `stop_reason`, and either finish (`end_turn`), run the
//! requested tools and loop (`tool_use`), or stop because the model ended for another reason. Every tool call
//! passes the gate first, and failures become error results the model can read.
//!
//! The loop depends only on abstractions ([`ModelClient`], [`ToolRegistry`], [`Gate`]), so any provider or tool
//! set can be plugged in. It holds no state between runs.

use std::fmt;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::Arc;

use crate::guard::{Decision, Gate, HumanApprover, ResultFilter};
use crate::instructions::Instructions;
use crate::model::{
    Message, ModelClient, ModelRequest, ModelResponse, StopReason, ToolResult, ToolUse,
};
use crate::state::{AgentState, RunStatus};
use crate::tool::{ToolOutput, ToolRegistry};

/// Why a [`LoopConfig`] was rejected.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ConfigError(pub String);

impl fmt::Display for ConfigError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl std::error::Error for ConfigError {}

/// Limits for one run. The iteration cap is a guard rail against runaway loops, never the normal way to stop.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct LoopConfig {
    max_iterations: u32,
    max_tokens: u32,
}

impl LoopConfig {
    /// Create a config. Both limits must be positive.
    pub fn new(max_iterations: u32, max_tokens: u32) -> Result<Self, ConfigError> {
        if max_iterations == 0 || max_tokens == 0 {
            return Err(ConfigError(
                "max_iterations and max_tokens must be positive".to_string(),
            ));
        }
        Ok(LoopConfig {
            max_iterations,
            max_tokens,
        })
    }

    /// 20 iterations and 1024 output tokens.
    pub fn defaults() -> Self {
        LoopConfig {
            max_iterations: 20,
            max_tokens: 1024,
        }
    }

    /// The iteration cap.
    pub fn max_iterations(&self) -> u32 {
        self.max_iterations
    }

    /// The output token limit sent on every call.
    pub fn max_tokens(&self) -> u32 {
        self.max_tokens
    }

    /// A copy with a different iteration cap.
    pub fn with_max_iterations(self, value: u32) -> Result<Self, ConfigError> {
        LoopConfig::new(value, self.max_tokens)
    }

    /// A copy with a different token limit.
    pub fn with_max_tokens(self, value: u32) -> Result<Self, ConfigError> {
        LoopConfig::new(self.max_iterations, value)
    }
}

impl Default for LoopConfig {
    fn default() -> Self {
        LoopConfig::defaults()
    }
}

/// The outcome of one run, copied out of the state so callers cannot change it.
#[derive(Debug, Clone, PartialEq)]
pub struct AgentResult {
    /// How the run ended.
    pub status: RunStatus,
    /// The model's final text, empty unless the run completed.
    pub text: String,
    /// An explanation when the run stopped abnormally.
    pub note: String,
    /// How many model calls were made.
    pub iterations: u32,
    /// The stop reason of the last response.
    pub last_stop_reason: Option<StopReason>,
    /// The full message history.
    pub history: Vec<Message>,
}

impl AgentResult {
    /// True when the model finished normally.
    pub fn completed(&self) -> bool {
        self.status == RunStatus::Completed
    }

    fn from_state(state: &AgentState) -> Self {
        AgentResult {
            status: state.status(),
            text: state.final_text().to_string(),
            note: state.note().to_string(),
            iterations: state.iterations(),
            last_stop_reason: state.last_stop_reason(),
            history: state.history().to_vec(),
        }
    }
}

/// The agent loop. Build it directly or through [`Agent`](crate::agent::Agent).
#[derive(Clone)]
pub struct AgentLoop {
    model: Arc<dyn ModelClient>,
    instructions: Instructions,
    tools: ToolRegistry,
    gate: Gate,
    approver: HumanApprover,
    filter: ResultFilter,
    config: LoopConfig,
}

impl AgentLoop {
    /// Create a loop from its parts.
    pub fn new(
        model: Arc<dyn ModelClient>,
        instructions: Instructions,
        tools: ToolRegistry,
        gate: Gate,
        approver: HumanApprover,
        filter: ResultFilter,
        config: LoopConfig,
    ) -> Self {
        AgentLoop {
            model,
            instructions,
            tools,
            gate,
            approver,
            filter,
            config,
        }
    }

    /// The tools this loop can run.
    pub fn tools(&self) -> &ToolRegistry {
        &self.tools
    }

    /// Run to completion and return the outcome. Each call uses a fresh, isolated state.
    pub fn run(&self, goal: &str) -> AgentResult {
        let mut state = AgentState::start(goal);
        let system = self.instructions.render();

        while state.status() == RunStatus::Running {
            if state.iterations() >= self.config.max_iterations() {
                state.stop(
                    RunStatus::LimitReached,
                    format!("Iteration cap of {} reached", self.config.max_iterations()),
                );
                break;
            }
            state.increment_iteration();

            let request = ModelRequest::new(
                system.clone(),
                state.history().to_vec(),
                self.tools.definitions(),
                self.config.max_tokens(),
            );
            let response = match self.model.send(&request) {
                Ok(response) => response,
                Err(error) => {
                    state.stop(
                        RunStatus::Failed,
                        format!("Model call failed: {}", error.message),
                    );
                    break;
                }
            };

            state.record_stop_reason(response.stop_reason);
            state.append(Message::assistant(response.content.clone()));

            match response.stop_reason {
                StopReason::EndTurn => state.complete(response.text()),
                StopReason::ToolUse => self.handle_tool_use(&response, &mut state),
                other => state.stop(
                    RunStatus::NotFinished,
                    format!("Model stopped with {other}, which is not a normal finish"),
                ),
            }
        }
        AgentResult::from_state(&state)
    }

    fn handle_tool_use(&self, response: &ModelResponse, state: &mut AgentState) {
        let requests = response.tool_uses();
        if requests.is_empty() {
            state.stop(
                RunStatus::Failed,
                "stop_reason was tool_use but the response had no tool_use block",
            );
            return;
        }
        let results: Vec<ToolResult> = requests
            .into_iter()
            .map(|request| self.run_one(request, state))
            .collect();
        // All results for one assistant turn go back together in a single user message.
        state.append(Message::tool_results(results));
    }

    fn run_one(&self, request: &ToolUse, state: &mut AgentState) -> ToolResult {
        let output = match self.gate.check(request, state) {
            Decision::Allow => self.execute(request, state),
            Decision::Deny(reason) => ToolOutput::error(format!("Blocked: {reason}")),
            Decision::AskHuman(reason) => {
                if self.approver.approve(request, &reason) {
                    self.execute(request, state)
                } else {
                    ToolOutput::error(format!("Not approved by a human: {reason}"))
                }
            }
        };
        let visible = self.filter.apply(request, output);
        ToolResult {
            tool_use_id: request.id.clone(),
            content: visible.content,
            is_error: visible.is_error,
        }
    }

    fn execute(&self, request: &ToolUse, state: &mut AgentState) -> ToolOutput {
        let Some(tool) = self.tools.find(&request.name) else {
            return ToolOutput::error(format!(
                "Unknown tool '{}'. Available: [{}]",
                request.name,
                self.tools.names().join(", ")
            ));
        };
        // A panic inside a tool is a failure of that tool, not of the whole agent. Its message is not
        // forwarded to the model, so internal details do not leak.
        match catch_unwind(AssertUnwindSafe(|| tool.execute(&request.input))) {
            Ok(Ok(output)) => {
                if !output.is_error {
                    state.mark_tool_succeeded(&request.name);
                }
                output
            }
            Ok(Err(error)) => ToolOutput::error(error.message),
            Err(_) => ToolOutput::error(format!("Tool '{}' failed unexpectedly", request.name)),
        }
    }
}
