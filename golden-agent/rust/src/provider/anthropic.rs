//! A [`ModelClient`] for the Anthropic Messages API.
//!
//! The Rust standard library has no HTTPS client, so this adapter is generic over an [`HttpTransport`] that
//! **you supply**, usually a thin wrapper over the HTTP crate your project already uses. Everything else is
//! here: request building, response parsing, retries with exponential backoff, and strict `stop_reason`
//! handling (an unknown value is rejected instead of being treated as a normal finish).
//!
//! It is a reference adapter. The request and response mapping is covered by offline tests with a mock
//! transport, but it has not been run against the live API from this repository, so verify it with your own
//! key before depending on it. The model id is always supplied by the caller.
//!
//! ```ignore
//! use std::time::Duration;
//! use golden_agent::provider::anthropic::{AnthropicModelClient, HttpResponse, HttpTransport, TransportError};
//!
//! struct MyTransport; // wrap reqwest, ureq, hyper or anything else
//!
//! impl HttpTransport for MyTransport {
//!     fn post(&self, url: &str, headers: &[(&str, &str)], body: &str, timeout: Duration)
//!         -> Result<HttpResponse, TransportError> {
//!         // send an HTTPS POST and return the status code and the body text
//!         # unimplemented!()
//!     }
//! }
//!
//! let client = AnthropicModelClient::from_environment("<model id>", MyTransport)?;
//! ```

use std::error::Error;
use std::fmt;
use std::thread;
use std::time::Duration;

use crate::json::Json;
use crate::model::{
    ContentBlock, ModelClient, ModelError, ModelRequest, ModelResponse, Role, StopReason, ToolUse,
};
use crate::tool::ToolInput;

const DEFAULT_ENDPOINT: &str = "https://api.anthropic.com/v1/messages";
const API_VERSION: &str = "2023-06-01";

/// An HTTP response: the status code and the body text.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HttpResponse {
    /// The HTTP status code.
    pub status: u16,
    /// The response body.
    pub body: String,
}

/// The request could not be completed at all (connection, timeout, TLS).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TransportError(pub String);

impl fmt::Display for TransportError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl Error for TransportError {}

/// Sends one HTTPS POST. Implement it with whichever HTTP client your project uses.
pub trait HttpTransport: Send + Sync {
    /// POST `body` to `url` with the given headers and return the status and body.
    fn post(
        &self,
        url: &str,
        headers: &[(&str, &str)],
        body: &str,
        timeout: Duration,
    ) -> Result<HttpResponse, TransportError>;
}

/// The Anthropic Messages API client.
pub struct AnthropicModelClient<T: HttpTransport> {
    transport: T,
    endpoint: String,
    api_key: String,
    model: String,
    timeout: Duration,
    max_retries: u32,
    retry_base_delay: Duration,
}

/// Builder for [`AnthropicModelClient`].
pub struct AnthropicBuilder<T: HttpTransport> {
    transport: T,
    endpoint: String,
    api_key: String,
    model: String,
    timeout: Duration,
    max_retries: u32,
    retry_base_delay: Duration,
}

impl<T: HttpTransport> AnthropicModelClient<T> {
    /// Start building a client with an explicit key and model id.
    pub fn builder(
        api_key: impl Into<String>,
        model: impl Into<String>,
        transport: T,
    ) -> AnthropicBuilder<T> {
        AnthropicBuilder {
            transport,
            endpoint: DEFAULT_ENDPOINT.to_string(),
            api_key: api_key.into(),
            model: model.into(),
            timeout: Duration::from_secs(60),
            max_retries: 2,
            retry_base_delay: Duration::from_millis(500),
        }
    }

    /// Read the key from the `ANTHROPIC_API_KEY` environment variable.
    pub fn from_environment(model: impl Into<String>, transport: T) -> Result<Self, ModelError> {
        let key = std::env::var("ANTHROPIC_API_KEY").unwrap_or_default();
        Self::builder(key, model, transport).build()
    }
}

impl<T: HttpTransport> AnthropicBuilder<T> {
    /// Use a different endpoint, for example a proxy.
    pub fn endpoint(mut self, endpoint: impl Into<String>) -> Self {
        self.endpoint = endpoint.into();
        self
    }

    /// The per-request timeout passed to the transport.
    pub fn timeout(mut self, timeout: Duration) -> Self {
        self.timeout = timeout;
        self
    }

    /// How many times to retry a rate limit or server error. The default is 2.
    pub fn max_retries(mut self, max_retries: u32) -> Self {
        self.max_retries = max_retries;
        self
    }

    /// The first retry delay. Each later retry doubles it. The default is 500 milliseconds.
    pub fn retry_base_delay(mut self, delay: Duration) -> Self {
        self.retry_base_delay = delay;
        self
    }

    /// Build the client, rejecting a blank key or model id.
    pub fn build(self) -> Result<AnthropicModelClient<T>, ModelError> {
        if self.api_key.trim().is_empty() {
            return Err(ModelError::new(
                "apiKey is required (is ANTHROPIC_API_KEY set?)",
            ));
        }
        if self.model.trim().is_empty() {
            return Err(ModelError::new("model is required"));
        }
        Ok(AnthropicModelClient {
            transport: self.transport,
            endpoint: self.endpoint,
            api_key: self.api_key,
            model: self.model,
            timeout: self.timeout,
            max_retries: self.max_retries,
            retry_base_delay: self.retry_base_delay,
        })
    }
}

impl<T: HttpTransport> ModelClient for AnthropicModelClient<T> {
    fn send(&self, request: &ModelRequest) -> Result<ModelResponse, ModelError> {
        let body = build_body(&self.model, request);
        let headers = [
            ("content-type", "application/json"),
            ("x-api-key", self.api_key.as_str()),
            ("anthropic-version", API_VERSION),
        ];

        let mut last: Option<ModelError> = None;
        for attempt in 0..=self.max_retries {
            if attempt > 0 {
                thread::sleep(self.retry_base_delay * (1u32 << (attempt - 1).min(10)));
            }
            match self
                .transport
                .post(&self.endpoint, &headers, &body, self.timeout)
            {
                Ok(response) if response.status == 200 => return parse_response(&response.body),
                Ok(response) => {
                    let detail =
                        format!("HTTP {}: {}", response.status, abbreviate(&response.body));
                    if response.status == 429 || response.status >= 500 {
                        last = Some(ModelError::new(detail));
                    } else {
                        return Err(ModelError::new(detail));
                    }
                }
                Err(error) => {
                    last = Some(ModelError::new(format!("Network error: {error}")));
                }
            }
        }
        Err(last.unwrap_or_else(|| ModelError::new("No request was made")))
    }
}

/// Build the JSON request body for the Messages API.
pub fn build_body(model: &str, request: &ModelRequest) -> String {
    let mut body: Vec<(&str, Json)> = vec![
        ("model", Json::from(model)),
        ("max_tokens", Json::from(request.max_tokens)),
    ];
    if !request.system.trim().is_empty() {
        body.push(("system", Json::from(request.system.as_str())));
    }
    let messages = request.messages.iter().map(|message| {
        Json::object([
            (
                "role",
                Json::from(match message.role {
                    Role::User => "user",
                    Role::Assistant => "assistant",
                }),
            ),
            (
                "content",
                Json::array(message.content.iter().map(block_to_json)),
            ),
        ])
    });
    body.push(("messages", Json::array(messages)));
    if !request.tools.is_empty() {
        let tools = request.tools.iter().map(|tool| {
            Json::object([
                ("name", Json::from(tool.name())),
                ("description", Json::from(tool.description())),
                ("input_schema", tool.input_schema().to_json_schema()),
            ])
        });
        body.push(("tools", Json::array(tools)));
    }
    Json::object(body).write()
}

fn block_to_json(block: &ContentBlock) -> Json {
    match block {
        ContentBlock::Text(text) => Json::object([
            ("type", Json::from("text")),
            ("text", Json::from(text.as_str())),
        ]),
        ContentBlock::ToolUse(use_) => Json::object([
            ("type", Json::from("tool_use")),
            ("id", Json::from(use_.id.as_str())),
            ("name", Json::from(use_.name.as_str())),
            (
                "input",
                Json::object(use_.input.iter().map(|(k, v)| (k.clone(), v.clone()))),
            ),
        ]),
        ContentBlock::ToolResult(result) => {
            let mut fields = vec![
                ("type", Json::from("tool_result")),
                ("tool_use_id", Json::from(result.tool_use_id.as_str())),
                ("content", Json::from(result.content.as_str())),
            ];
            if result.is_error {
                fields.push(("is_error", Json::Bool(true)));
            }
            Json::object(fields)
        }
    }
}

/// Parse a Messages API response body into a [`ModelResponse`].
///
/// An unknown `stop_reason` is an error. Content block types this library does not use are skipped.
pub fn parse_response(json: &str) -> Result<ModelResponse, ModelError> {
    let root = Json::parse(json)
        .map_err(|e| ModelError::new(format!("Response was not valid JSON: {e}")))?;
    if root.as_object().is_none() {
        return Err(ModelError::new("Response was not a JSON object"));
    }
    let items = root
        .get("content")
        .and_then(Json::as_array)
        .ok_or_else(|| ModelError::new("Response has no content array"))?;

    let mut blocks = Vec::new();
    for item in items {
        match item.get("type").and_then(Json::as_str) {
            Some("text") => {
                let text = item.get("text").and_then(Json::as_str).unwrap_or("");
                blocks.push(ContentBlock::Text(text.to_string()));
            }
            Some("tool_use") => {
                let input: ToolInput = item
                    .get("input")
                    .and_then(Json::as_object)
                    .map(|pairs| pairs.iter().cloned().collect())
                    .unwrap_or_default();
                blocks.push(ContentBlock::ToolUse(ToolUse {
                    id: item
                        .get("id")
                        .and_then(Json::as_str)
                        .unwrap_or("")
                        .to_string(),
                    name: item
                        .get("name")
                        .and_then(Json::as_str)
                        .unwrap_or("")
                        .to_string(),
                    input,
                }));
            }
            _ => {}
        }
    }
    let reason = root
        .get("stop_reason")
        .and_then(Json::as_str)
        .ok_or_else(|| ModelError::new("Response has no stop_reason"))?;
    Ok(ModelResponse {
        content: blocks,
        stop_reason: stop_reason_from_str(reason)?,
    })
}

/// Map an API `stop_reason` string to a [`StopReason`]. Unknown values are rejected.
pub fn stop_reason_from_str(value: &str) -> Result<StopReason, ModelError> {
    match value {
        "end_turn" => Ok(StopReason::EndTurn),
        "tool_use" => Ok(StopReason::ToolUse),
        "max_tokens" => Ok(StopReason::MaxTokens),
        "stop_sequence" => Ok(StopReason::StopSequence),
        "pause_turn" => Ok(StopReason::PauseTurn),
        "refusal" => Ok(StopReason::Refusal),
        "model_context_window_exceeded" => Ok(StopReason::ContextWindowExceeded),
        other => Err(ModelError::new(format!("Unknown stop_reason '{other}'"))),
    }
}

fn abbreviate(text: &str) -> String {
    if text.chars().count() <= 300 {
        text.to_string()
    } else {
        let cut: String = text.chars().take(300).collect();
        format!("{cut}...")
    }
}
