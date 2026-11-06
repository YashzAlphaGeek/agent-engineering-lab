//! Element 5, the state of one run.
//!
//! Holds the ordered message history (the agent's memory, sent on every model call), the goal, the iteration
//! count and the outcome. A new instance is created for every run and instances are never shared between
//! agents or runs, which keeps each agent's context isolated.

use std::collections::BTreeSet;
use std::fmt;
use std::sync::atomic::{AtomicU64, Ordering};

use crate::model::{Message, StopReason};

static NEXT_RUN: AtomicU64 = AtomicU64::new(1);

/// Where a run stands. Only `Completed` means the model finished normally.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RunStatus {
    /// The loop is still going.
    Running,
    /// The model ended its turn normally (`end_turn`).
    Completed,
    /// The iteration cap stopped the run. This is a guard rail, not a normal finish.
    LimitReached,
    /// The model stopped for another reason, such as `max_tokens` or a refusal.
    NotFinished,
    /// A model call failed or the response was malformed.
    Failed,
}

impl fmt::Display for RunStatus {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(match self {
            RunStatus::Running => "RUNNING",
            RunStatus::Completed => "COMPLETED",
            RunStatus::LimitReached => "LIMIT_REACHED",
            RunStatus::NotFinished => "NOT_FINISHED",
            RunStatus::Failed => "FAILED",
        })
    }
}

/// The state of one run.
#[derive(Debug)]
pub struct AgentState {
    run_id: String,
    goal: String,
    history: Vec<Message>,
    succeeded_tools: BTreeSet<String>,
    iterations: u32,
    status: RunStatus,
    last_stop_reason: Option<StopReason>,
    final_text: String,
    note: String,
}

impl AgentState {
    /// Start a run. The goal becomes the first user message.
    pub fn start(goal: impl Into<String>) -> Self {
        let goal = goal.into();
        AgentState {
            run_id: format!("run-{}", NEXT_RUN.fetch_add(1, Ordering::Relaxed)),
            history: vec![Message::user(goal.clone())],
            goal,
            succeeded_tools: BTreeSet::new(),
            iterations: 0,
            status: RunStatus::Running,
            last_stop_reason: None,
            final_text: String::new(),
            note: String::new(),
        }
    }

    /// A process-unique id for this run.
    pub fn run_id(&self) -> &str {
        &self.run_id
    }

    /// The goal this run was started with.
    pub fn goal(&self) -> &str {
        &self.goal
    }

    /// The ordered message history.
    pub fn history(&self) -> &[Message] {
        &self.history
    }

    /// How many model calls have been made.
    pub fn iterations(&self) -> u32 {
        self.iterations
    }

    /// The current status.
    pub fn status(&self) -> RunStatus {
        self.status
    }

    /// The stop reason of the most recent model response.
    pub fn last_stop_reason(&self) -> Option<StopReason> {
        self.last_stop_reason
    }

    /// The final text, empty until the run completes.
    pub fn final_text(&self) -> &str {
        &self.final_text
    }

    /// An explanation of why the run stopped abnormally.
    pub fn note(&self) -> &str {
        &self.note
    }

    /// Append a message to the history.
    pub fn append(&mut self, message: Message) {
        self.history.push(message);
    }

    /// Count one more model call.
    pub fn increment_iteration(&mut self) {
        self.iterations += 1;
    }

    /// Remember the stop reason of the latest response.
    pub fn record_stop_reason(&mut self, reason: StopReason) {
        self.last_stop_reason = Some(reason);
    }

    /// Remember that a tool ran successfully. Gates use this to enforce prerequisites.
    pub fn mark_tool_succeeded(&mut self, tool_name: &str) {
        self.succeeded_tools.insert(tool_name.to_string());
    }

    /// True when the named tool has already succeeded in this run.
    pub fn has_succeeded(&self, tool_name: &str) -> bool {
        self.succeeded_tools.contains(tool_name)
    }

    /// Finish normally with the model's text.
    pub fn complete(&mut self, text: impl Into<String>) {
        self.status = RunStatus::Completed;
        self.final_text = text.into();
    }

    /// Stop abnormally. Use [`AgentState::complete`] for a normal finish.
    ///
    /// # Panics
    ///
    /// Panics if `reason` is `Running` or `Completed`.
    pub fn stop(&mut self, reason: RunStatus, explanation: impl Into<String>) {
        assert!(
            !matches!(reason, RunStatus::Running | RunStatus::Completed),
            "use complete() for a normal finish"
        );
        self.status = reason;
        self.note = explanation.into();
    }
}
