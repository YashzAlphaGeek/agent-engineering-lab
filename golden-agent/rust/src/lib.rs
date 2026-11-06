#![forbid(unsafe_code)]
#![warn(missing_docs)]
//! # golden-agent
//!
//! A small library for building agents from the core elements: **model, instructions, tools,
//! loop and state**, plus a guard layer (gates, human approval, result filters) and sub agents.
//! It uses only the Rust standard library.
//!
//! The design rule behind it: the LLM proposes and never executes. Your code validates, gates
//! and runs every action.
//!
//! ```
//! use std::sync::Arc;
//! use golden_agent::prelude::*;
//!
//! // A scripted model stands in for a real LLM so the example is deterministic.
//! let model = Arc::new(ScriptedModelClient::new([
//!     ScriptedModelClient::tool_use("t1", "echo", tool_input([("text", Json::from("hi"))])),
//!     ScriptedModelClient::end_turn("The tool said echo:hi"),
//! ]));
//!
//! let echo = FunctionTool::new(
//!     ToolDefinition::new(
//!         "echo",
//!         "Echo the text back",
//!         InputSchema::object().string("text", "Text to echo", true).build(),
//!     )
//!     .unwrap(),
//!     |input| format!("echo:{}", input["text"].as_str().unwrap_or("")),
//! );
//!
//! let agent = Agent::builder("demo")
//!     .model(model)
//!     .instructions(Instructions::for_role("You are a demo agent."))
//!     .tool(echo)
//!     .build()
//!     .unwrap();
//!
//! let result = agent.run("Say hi through the tool");
//! assert!(result.completed());
//! assert_eq!(result.iterations, 2);
//! ```

pub mod agent;
pub mod agent_loop;
pub mod guard;
pub mod instructions;
pub mod json;
pub mod model;
pub mod provider;
pub mod state;
pub mod tool;

/// The most commonly used items, for a single `use golden_agent::prelude::*;`.
pub mod prelude {
    pub use crate::agent::{Agent, AgentBuilder, AgentError, DelegationTool};
    pub use crate::agent_loop::{AgentLoop, AgentResult, LoopConfig};
    pub use crate::guard::{Decision, Gate, Gates, HumanApprover, ResultFilter};
    pub use crate::instructions::Instructions;
    pub use crate::json::Json;
    pub use crate::model::{
        ContentBlock, Message, ModelClient, ModelError, ModelRequest, ModelResponse, Role,
        ScriptedModelClient, StopReason, ToolResult, ToolUse,
    };
    pub use crate::state::{AgentState, RunStatus};
    pub use crate::tool::{
        FunctionTool, InputSchema, Tool, ToolDefinition, ToolError, ToolInput, ToolOutput,
        ToolRegistry, TypedTool, integer_arg, string_arg, tool_input,
    };
}
