//! The public entry point of the library: one agent made of the five core elements.
//!
//! An [`Agent`] is immutable and holds no run state, so one instance can be shared (for example in an `Arc`)
//! and run many times, even concurrently. Every [`Agent::run`] creates its own isolated state.

use std::error::Error;
use std::fmt;
use std::sync::Arc;

use crate::agent_loop::{AgentLoop, AgentResult, ConfigError, LoopConfig};
use crate::guard::{Gate, HumanApprover, ResultFilter};
use crate::instructions::Instructions;
use crate::model::ModelClient;
use crate::tool::{
    InputSchema, RegistryError, Tool, ToolDefinition, ToolDefinitionError, ToolError, ToolInput,
    ToolOutput, ToolRegistry,
};

/// Why an agent could not be built.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AgentError {
    /// The name is not 1 to 50 characters of letters, digits, `_` or `-`.
    InvalidName(String),
    /// No model was supplied.
    MissingModel,
    /// No instructions were supplied.
    MissingInstructions,
    /// The loop limits were rejected.
    InvalidConfig(String),
    /// Two tools share a name, or a tool name was unknown.
    Registry(RegistryError),
    /// A tool definition was invalid.
    Definition(ToolDefinitionError),
}

impl fmt::Display for AgentError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AgentError::InvalidName(n) => {
                write!(f, "Agent name must match [a-zA-Z0-9_-]{{1,50}}: {n}")
            }
            AgentError::MissingModel => f.write_str("model is required"),
            AgentError::MissingInstructions => f.write_str("instructions are required"),
            AgentError::InvalidConfig(m) => f.write_str(m),
            AgentError::Registry(e) => e.fmt(f),
            AgentError::Definition(e) => e.fmt(f),
        }
    }
}

impl Error for AgentError {}

impl From<RegistryError> for AgentError {
    fn from(value: RegistryError) -> Self {
        AgentError::Registry(value)
    }
}

impl From<ToolDefinitionError> for AgentError {
    fn from(value: ToolDefinitionError) -> Self {
        AgentError::Definition(value)
    }
}

impl From<ConfigError> for AgentError {
    fn from(value: ConfigError) -> Self {
        AgentError::InvalidConfig(value.0)
    }
}

/// One agent: model, instructions, tools, loop limits and guards.
#[derive(Clone)]
pub struct Agent {
    name: String,
    description: String,
    runner: AgentLoop,
}

impl fmt::Debug for Agent {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Agent")
            .field("name", &self.name)
            .field("tools", self.runner.tools())
            .finish()
    }
}

impl Agent {
    /// Start building an agent.
    pub fn builder(name: &str) -> AgentBuilder {
        AgentBuilder::new(name)
    }

    /// The agent name.
    pub fn name(&self) -> &str {
        &self.name
    }

    /// What this agent is for. When used as a sub agent this is what the coordinator reads.
    pub fn description(&self) -> &str {
        &self.description
    }

    /// The tools this agent can run.
    pub fn tools(&self) -> &ToolRegistry {
        self.runner.tools()
    }

    /// Run the agent on a goal. Safe to call repeatedly and concurrently.
    pub fn run(&self, goal: &str) -> AgentResult {
        self.runner.run(goal)
    }

    /// Expose this agent to another agent as a delegation tool (the sub agent pattern).
    pub fn as_tool(self: &Arc<Self>) -> Result<Arc<dyn Tool>, ToolDefinitionError> {
        Ok(Arc::new(DelegationTool::new(Arc::clone(self))?))
    }
}

fn valid_agent_name(name: &str) -> bool {
    !name.is_empty()
        && name.len() <= 50
        && name
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '-')
}

/// Builder for [`Agent`]. Problems found along the way are reported by [`AgentBuilder::build`].
pub struct AgentBuilder {
    name: String,
    description: String,
    model: Option<Arc<dyn ModelClient>>,
    instructions: Option<Instructions>,
    tools: ToolRegistry,
    gate: Gate,
    approver: HumanApprover,
    filter: ResultFilter,
    max_iterations: u32,
    max_tokens: u32,
    error: Option<AgentError>,
}

impl AgentBuilder {
    fn new(name: &str) -> Self {
        let defaults = LoopConfig::defaults();
        AgentBuilder {
            name: name.to_string(),
            description: format!("Delegates a task to the {name} agent"),
            model: None,
            instructions: None,
            tools: ToolRegistry::new(),
            gate: Gate::allow_all(),
            approver: HumanApprover::deny_all(),
            filter: ResultFilter::identity(),
            max_iterations: defaults.max_iterations(),
            max_tokens: defaults.max_tokens(),
            error: None,
        }
    }

    fn fail(mut self, error: impl Into<AgentError>) -> Self {
        self.error.get_or_insert(error.into());
        self
    }

    /// What this agent is for.
    pub fn description(mut self, description: &str) -> Self {
        self.description = description.to_string();
        self
    }

    /// The model client. An `Arc` of a client is accepted, so you can keep your own handle to it.
    pub fn model(mut self, model: impl ModelClient + 'static) -> Self {
        self.model = Some(Arc::new(model));
        self
    }

    /// The instructions.
    pub fn instructions(mut self, instructions: Instructions) -> Self {
        self.instructions = Some(instructions);
        self
    }

    /// Add a tool.
    pub fn tool(self, tool: impl Tool + 'static) -> Self {
        self.tool_arc(Arc::new(tool))
    }

    /// Add a tool that is already shared.
    pub fn tool_arc(mut self, tool: Arc<dyn Tool>) -> Self {
        match self.tools.register(tool) {
            Ok(()) => self,
            Err(e) => self.fail(e),
        }
    }

    /// Add every tool from a registry, for example a restricted `subset`.
    pub fn registry(mut self, registry: &ToolRegistry) -> Self {
        for tool in registry.all() {
            self = self.tool_arc(tool);
        }
        self
    }

    /// Give this agent a sub agent, exposed as a delegation tool.
    pub fn sub_agent(self, sub: Arc<Agent>) -> Self {
        match sub.as_tool() {
            Ok(tool) => self.tool_arc(tool),
            Err(e) => self.fail(e),
        }
    }

    /// Add a gate. Several gates combine, and the first non-Allow decision wins.
    pub fn gate(mut self, gate: Gate) -> Self {
        self.gate = self.gate.clone().and(gate);
        self
    }

    /// The human approval channel. The default denies everything.
    pub fn approver(mut self, approver: HumanApprover) -> Self {
        self.approver = approver;
        self
    }

    /// A filter applied to every tool result before the model sees it.
    pub fn result_filter(mut self, filter: ResultFilter) -> Self {
        self.filter = filter;
        self
    }

    /// Set both loop limits.
    pub fn config(mut self, config: LoopConfig) -> Self {
        self.max_iterations = config.max_iterations();
        self.max_tokens = config.max_tokens();
        self
    }

    /// Set the iteration cap.
    pub fn max_iterations(mut self, max: u32) -> Self {
        self.max_iterations = max;
        self
    }

    /// Build the agent, or report the first problem found.
    pub fn build(self) -> Result<Agent, AgentError> {
        if let Some(error) = self.error {
            return Err(error);
        }
        if !valid_agent_name(&self.name) {
            return Err(AgentError::InvalidName(self.name));
        }
        let model = self.model.ok_or(AgentError::MissingModel)?;
        let instructions = self.instructions.ok_or(AgentError::MissingInstructions)?;
        let config = LoopConfig::new(self.max_iterations, self.max_tokens)?;
        Ok(Agent {
            name: self.name,
            description: self.description,
            runner: AgentLoop::new(
                model,
                instructions,
                self.tools,
                self.gate,
                self.approver,
                self.filter,
                config,
            ),
        })
    }
}

/// A tool whose implementation is another agent. This is how a coordinator delegates to a sub agent.
///
/// The sub agent never sees the coordinator's conversation. It receives only the `task` and the explicit
/// `context` that the coordinator passes. When the sub agent does not finish, the tool returns a structured
/// error (what was attempted, status, partial text) instead of an empty success, so the coordinator can retry,
/// route elsewhere, or continue with partial results.
pub struct DelegationTool {
    agent: Arc<Agent>,
    definition: ToolDefinition,
}

impl DelegationTool {
    /// Wrap an agent as a tool named `delegate_to_<name>`.
    pub fn new(agent: Arc<Agent>) -> Result<Self, ToolDefinitionError> {
        let definition = ToolDefinition::new(
            format!("delegate_to_{}", agent.name()),
            agent.description(),
            InputSchema::object()
                .string("task", "The sub-task to perform, stated completely", true)
                .string(
                    "context",
                    "Everything the sub agent needs, such as goal and prior findings",
                    false,
                )
                .build(),
        )?;
        Ok(DelegationTool { agent, definition })
    }
}

impl Tool for DelegationTool {
    fn definition(&self) -> &ToolDefinition {
        &self.definition
    }

    fn execute(&self, input: &ToolInput) -> Result<ToolOutput, ToolError> {
        let task = match input.get("task").and_then(|t| t.as_str()) {
            Some(t) if !t.trim().is_empty() => t,
            _ => return Err(ToolError::new("Missing required argument 'task'")),
        };
        let context = input.get("context").and_then(|c| c.as_str()).unwrap_or("");
        let goal = if context.trim().is_empty() {
            task.to_string()
        } else {
            format!("{task}\n\nContext:\n{context}")
        };

        let result = self.agent.run(&goal);
        if result.completed() {
            return Ok(ToolOutput::ok(result.text));
        }
        Ok(ToolOutput::error(format!(
            "SUB_AGENT_FAILURE agent={} status={} iterations={} note=\"{}\" task=\"{}\" partial=\"{}\"",
            self.agent.name(),
            result.status,
            result.iterations,
            result.note,
            task,
            result.text
        )))
    }
}
