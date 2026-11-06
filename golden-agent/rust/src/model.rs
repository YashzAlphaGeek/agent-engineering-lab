//! Element 1, the model. The only place an LLM is reached.
//!
//! Implement [`ModelClient`] once per provider (Anthropic API, Bedrock, Vertex, a test double).
//! The loop depends on the trait only, so providers are interchangeable.

use std::collections::VecDeque;
use std::error::Error;
use std::fmt;
use std::sync::{Arc, Mutex, PoisonError};

use crate::tool::{ToolDefinition, ToolInput};

/// Who produced a message in the conversation history.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Role {
    /// The user, or tool results travelling back to the model.
    User,
    /// The model.
    Assistant,
}

/// The model asks the application to run a tool. It is a request, never an execution.
#[derive(Debug, Clone, PartialEq)]
pub struct ToolUse {
    /// The id the matching result must carry.
    pub id: String,
    /// The requested tool name.
    pub name: String,
    /// The arguments.
    pub input: ToolInput,
}

/// The application's answer to a [`ToolUse`], matched by `tool_use_id`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToolResult {
    /// The id of the request being answered.
    pub tool_use_id: String,
    /// The text returned to the model.
    pub content: String,
    /// True when the content describes a failure.
    pub is_error: bool,
}

/// One piece of a message. The three shapes mirror the Messages API.
#[derive(Debug, Clone, PartialEq)]
pub enum ContentBlock {
    /// Plain text from the user or the model.
    Text(String),
    /// A tool request from the model.
    ToolUse(ToolUse),
    /// The result of a tool request.
    ToolResult(ToolResult),
}

/// One turn in the conversation. The full list of messages is the agent's memory.
#[derive(Debug, Clone, PartialEq)]
pub struct Message {
    /// Who produced the message.
    pub role: Role,
    /// The content blocks.
    pub content: Vec<ContentBlock>,
}

impl Message {
    /// A user message with plain text.
    pub fn user(text: impl Into<String>) -> Self {
        Message {
            role: Role::User,
            content: vec![ContentBlock::Text(text.into())],
        }
    }

    /// An assistant message made of the given blocks.
    pub fn assistant(blocks: Vec<ContentBlock>) -> Self {
        Message {
            role: Role::Assistant,
            content: blocks,
        }
    }

    /// Tool results travel back to the model inside a user message.
    pub fn tool_results(results: Vec<ToolResult>) -> Self {
        Message {
            role: Role::User,
            content: results.into_iter().map(ContentBlock::ToolResult).collect(),
        }
    }
}

/// Why the model stopped. This is the only reliable signal for controlling the loop.
///
/// Anything other than `EndTurn` or `ToolUse` means "not finished, find out why".
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StopReason {
    /// The model finished normally.
    EndTurn,
    /// The model wants tools run.
    ToolUse,
    /// The output hit the token limit.
    MaxTokens,
    /// A custom stop sequence was produced.
    StopSequence,
    /// The turn was paused.
    PauseTurn,
    /// The model declined.
    Refusal,
    /// The context window was exceeded.
    ContextWindowExceeded,
}

impl fmt::Display for StopReason {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(match self {
            StopReason::EndTurn => "END_TURN",
            StopReason::ToolUse => "TOOL_USE",
            StopReason::MaxTokens => "MAX_TOKENS",
            StopReason::StopSequence => "STOP_SEQUENCE",
            StopReason::PauseTurn => "PAUSE_TURN",
            StopReason::Refusal => "REFUSAL",
            StopReason::ContextWindowExceeded => "CONTEXT_WINDOW_EXCEEDED",
        })
    }
}

/// Everything sent to the model on one call. The API is stateless, so history is always included.
#[derive(Debug, Clone, PartialEq)]
pub struct ModelRequest {
    /// The rendered system prompt.
    pub system: String,
    /// The full message history.
    pub messages: Vec<Message>,
    /// The tools the model may call.
    pub tools: Vec<ToolDefinition>,
    /// The output token limit.
    pub max_tokens: u32,
}

impl ModelRequest {
    /// Create a request.
    pub fn new(
        system: impl Into<String>,
        messages: Vec<Message>,
        tools: Vec<ToolDefinition>,
        max_tokens: u32,
    ) -> Self {
        ModelRequest {
            system: system.into(),
            messages,
            tools,
            max_tokens,
        }
    }
}

/// What comes back from the model: content blocks plus the reason it stopped.
#[derive(Debug, Clone, PartialEq)]
pub struct ModelResponse {
    /// The content blocks.
    pub content: Vec<ContentBlock>,
    /// Why the model stopped.
    pub stop_reason: StopReason,
}

impl ModelResponse {
    /// All text blocks joined with newlines.
    pub fn text(&self) -> String {
        self.content
            .iter()
            .filter_map(|block| match block {
                ContentBlock::Text(t) => Some(t.as_str()),
                _ => None,
            })
            .collect::<Vec<_>>()
            .join("\n")
    }

    /// All tool requests in this response. A response may contain several.
    pub fn tool_uses(&self) -> Vec<&ToolUse> {
        self.content
            .iter()
            .filter_map(|block| match block {
                ContentBlock::ToolUse(u) => Some(u),
                _ => None,
            })
            .collect()
    }
}

/// A model call failed (network, rate limit, overload, malformed reply). The loop turns it into a `Failed` run.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ModelError {
    /// What went wrong.
    pub message: String,
}

impl ModelError {
    /// Create a model error.
    pub fn new(message: impl Into<String>) -> Self {
        ModelError {
            message: message.into(),
        }
    }
}

impl fmt::Display for ModelError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl Error for ModelError {}

/// The model element. Implement it once per provider.
///
/// A closure `Fn(&ModelRequest) -> Result<ModelResponse, ModelError>` is also a `ModelClient`, and so is an
/// `Arc` of any client, which lets a test keep a handle to a [`ScriptedModelClient`] it gave to an agent.
pub trait ModelClient: Send + Sync {
    /// Send one request and return the model's response.
    fn send(&self, request: &ModelRequest) -> Result<ModelResponse, ModelError>;
}

impl<F> ModelClient for F
where
    F: Fn(&ModelRequest) -> Result<ModelResponse, ModelError> + Send + Sync,
{
    fn send(&self, request: &ModelRequest) -> Result<ModelResponse, ModelError> {
        self(request)
    }
}

impl<T: ModelClient + ?Sized> ModelClient for Arc<T> {
    fn send(&self, request: &ModelRequest) -> Result<ModelResponse, ModelError> {
        (**self).send(request)
    }
}

/// A deterministic test double for [`ModelClient`]. It replays a fixed script of responses.
///
/// Running past the end of the script returns an error, so a loop that asks the model more often than
/// expected is caught instead of being hidden.
pub struct ScriptedModelClient {
    script: Mutex<VecDeque<ModelResponse>>,
    requests: Mutex<Vec<ModelRequest>>,
}

impl ScriptedModelClient {
    /// Create a client that replays these responses in order.
    pub fn new(responses: impl IntoIterator<Item = ModelResponse>) -> Self {
        ScriptedModelClient {
            script: Mutex::new(responses.into_iter().collect()),
            requests: Mutex::new(Vec::new()),
        }
    }

    /// Every request received so far, for assertions.
    pub fn requests(&self) -> Vec<ModelRequest> {
        self.requests
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .clone()
    }

    /// How many requests were received.
    pub fn calls(&self) -> usize {
        self.requests
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .len()
    }

    /// A script step where the model ends its turn with this text.
    pub fn end_turn(text: &str) -> ModelResponse {
        ModelResponse {
            content: vec![ContentBlock::Text(text.to_string())],
            stop_reason: StopReason::EndTurn,
        }
    }

    /// A script step where the model asks for one tool.
    pub fn tool_use(id: &str, tool_name: &str, input: ToolInput) -> ModelResponse {
        ModelResponse {
            content: vec![ContentBlock::ToolUse(ToolUse {
                id: id.to_string(),
                name: tool_name.to_string(),
                input,
            })],
            stop_reason: StopReason::ToolUse,
        }
    }

    /// A script step where the model stops for the given reason.
    pub fn stopped_with(reason: StopReason) -> ModelResponse {
        ModelResponse {
            content: vec![ContentBlock::Text("(stopped)".to_string())],
            stop_reason: reason,
        }
    }
}

impl ModelClient for ScriptedModelClient {
    fn send(&self, request: &ModelRequest) -> Result<ModelResponse, ModelError> {
        let calls = {
            let mut requests = self.requests.lock().unwrap_or_else(PoisonError::into_inner);
            requests.push(request.clone());
            requests.len()
        };
        self.script
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .pop_front()
            .ok_or_else(|| ModelError::new(format!("Script exhausted after {calls} calls")))
    }
}
