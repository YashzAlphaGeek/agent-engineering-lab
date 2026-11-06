//! The guard layer: programmatic checks around every tool call.
//!
//! A [`Gate`] is the veto. It runs before every tool call, outside the model, so the model cannot skip or
//! argue with it. It returns one of three [`Decision`]s: allow, deny, or ask a human.

use std::collections::{BTreeSet, HashMap};
use std::sync::Arc;

use crate::model::ToolUse;
use crate::state::AgentState;
use crate::tool::ToolOutput;

/// The three outcomes of a gate.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Decision {
    /// The call may run.
    Allow,
    /// The call must not run. The reason is returned to the model as an error result.
    Deny(String),
    /// A person must approve first. No answer means deny.
    AskHuman(String),
}

type GateFn = dyn Fn(&ToolUse, &AgentState) -> Decision + Send + Sync;

/// A programmatic check that runs before every tool call. Cheap to clone.
#[derive(Clone)]
pub struct Gate(Arc<GateFn>);

impl Gate {
    /// Create a gate from a closure.
    pub fn new(check: impl Fn(&ToolUse, &AgentState) -> Decision + Send + Sync + 'static) -> Self {
        Gate(Arc::new(check))
    }

    /// A gate that allows everything.
    pub fn allow_all() -> Self {
        Gate::new(|_, _| Decision::Allow)
    }

    /// Run the check.
    pub fn check(&self, call: &ToolUse, state: &AgentState) -> Decision {
        (self.0)(call, state)
    }

    /// Combine two gates. The first decision that is not `Allow` wins.
    pub fn and(self, next: Gate) -> Gate {
        Gate::new(move |call, state| match self.check(call, state) {
            Decision::Allow => next.check(call, state),
            other => other,
        })
    }
}

impl std::fmt::Debug for Gate {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("Gate(..)")
    }
}

/// Ready-made gates for the most common rules.
pub struct Gates;

impl Gates {
    /// Block a tool until other tools have succeeded first.
    ///
    /// Example: `Gates::prerequisites(&[("process_refund", &["get_customer"])])`.
    pub fn prerequisites(requires: &[(&str, &[&str])]) -> Gate {
        let map: HashMap<String, Vec<String>> = requires
            .iter()
            .map(|(tool, needed)| {
                (
                    (*tool).to_string(),
                    needed.iter().map(|n| (*n).to_string()).collect(),
                )
            })
            .collect();
        Gate::new(move |call, state| {
            let missing: BTreeSet<&String> = map
                .get(&call.name)
                .into_iter()
                .flatten()
                .filter(|needed| !state.has_succeeded(needed))
                .collect();
            if missing.is_empty() {
                Decision::Allow
            } else {
                let list: Vec<&str> = missing.iter().map(|s| s.as_str()).collect();
                Decision::Deny(format!(
                    "'{}' requires these tools to succeed first: [{}]",
                    call.name,
                    list.join(", ")
                ))
            }
        })
    }

    /// Ask a human before any of the named tools run.
    pub fn require_approval(tool_names: &[&str], reason: &str) -> Gate {
        let names: BTreeSet<String> = tool_names.iter().map(|n| (*n).to_string()).collect();
        let reason = reason.to_string();
        Gate::new(move |call, _| {
            if names.contains(&call.name) {
                Decision::AskHuman(reason.clone())
            } else {
                Decision::Allow
            }
        })
    }

    /// Only the named tools may run, whatever the registry holds.
    pub fn allow_list(tool_names: &[&str]) -> Gate {
        let names: BTreeSet<String> = tool_names.iter().map(|n| (*n).to_string()).collect();
        Gate::new(move |call, _| {
            if names.contains(&call.name) {
                Decision::Allow
            } else {
                Decision::Deny(format!(
                    "Tool '{}' is not permitted for this agent",
                    call.name
                ))
            }
        })
    }
}

type ApproveFn = dyn Fn(&ToolUse, &str) -> bool + Send + Sync;

/// Asked when a gate returns [`Decision::AskHuman`]. Plug in a console prompt, a ticket, a chat message or a
/// web form. The default fails closed: nothing is approved unless a person says so.
#[derive(Clone)]
pub struct HumanApprover(Arc<ApproveFn>);

impl HumanApprover {
    /// Create an approver from a closure that returns true to approve.
    pub fn new(approve: impl Fn(&ToolUse, &str) -> bool + Send + Sync + 'static) -> Self {
        HumanApprover(Arc::new(approve))
    }

    /// An approver that denies everything. This is the default.
    pub fn deny_all() -> Self {
        HumanApprover::new(|_, _| false)
    }

    /// Ask for approval.
    pub fn approve(&self, call: &ToolUse, reason: &str) -> bool {
        (self.0)(call, reason)
    }
}

impl Default for HumanApprover {
    fn default() -> Self {
        HumanApprover::deny_all()
    }
}

impl std::fmt::Debug for HumanApprover {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("HumanApprover(..)")
    }
}

type FilterFn = dyn Fn(&ToolUse, ToolOutput) -> ToolOutput + Send + Sync;

/// Runs after a tool and before the model sees the result. Use it to trim large output, normalise formats or
/// redact sensitive fields. Shrinking what a tool returns is the right way to save tokens, rather than
/// dropping results from the history.
#[derive(Clone)]
pub struct ResultFilter(Arc<FilterFn>);

impl ResultFilter {
    /// Create a filter from a closure.
    pub fn new(
        filter: impl Fn(&ToolUse, ToolOutput) -> ToolOutput + Send + Sync + 'static,
    ) -> Self {
        ResultFilter(Arc::new(filter))
    }

    /// A filter that changes nothing. This is the default.
    pub fn identity() -> Self {
        ResultFilter::new(|_, output| output)
    }

    /// Apply the filter.
    pub fn apply(&self, call: &ToolUse, output: ToolOutput) -> ToolOutput {
        (self.0)(call, output)
    }
}

impl Default for ResultFilter {
    fn default() -> Self {
        ResultFilter::identity()
    }
}

impl std::fmt::Debug for ResultFilter {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("ResultFilter(..)")
    }
}
