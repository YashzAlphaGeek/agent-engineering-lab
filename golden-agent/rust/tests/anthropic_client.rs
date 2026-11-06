//! Offline tests of the Anthropic adapter, using a mock transport. No network is used.

use std::collections::VecDeque;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use golden_agent::prelude::*;
use golden_agent::provider::anthropic::{
    AnthropicModelClient, HttpResponse, HttpTransport, TransportError,
};

/// One request the mock transport saw.
#[derive(Debug, Clone)]
struct Seen {
    url: String,
    headers: Vec<(String, String)>,
    body: String,
}

/// A transport that replays canned responses and records what it was asked to send.
#[derive(Clone, Default)]
struct MockTransport {
    replies: Arc<Mutex<VecDeque<Result<HttpResponse, TransportError>>>>,
    seen: Arc<Mutex<Vec<Seen>>>,
}

impl MockTransport {
    fn with(replies: Vec<Result<HttpResponse, TransportError>>) -> Self {
        MockTransport {
            replies: Arc::new(Mutex::new(replies.into())),
            seen: Arc::default(),
        }
    }

    fn calls(&self) -> usize {
        self.seen.lock().unwrap().len()
    }
}

impl HttpTransport for MockTransport {
    fn post(
        &self,
        url: &str,
        headers: &[(&str, &str)],
        body: &str,
        _timeout: Duration,
    ) -> Result<HttpResponse, TransportError> {
        self.seen.lock().unwrap().push(Seen {
            url: url.to_string(),
            headers: headers
                .iter()
                .map(|(k, v)| (k.to_string(), v.to_string()))
                .collect(),
            body: body.to_string(),
        });
        self.replies
            .lock()
            .unwrap()
            .pop_front()
            .unwrap_or_else(|| Err(TransportError("mock exhausted".to_string())))
    }
}

fn reply(status: u16, body: &str) -> Result<HttpResponse, TransportError> {
    Ok(HttpResponse {
        status,
        body: body.to_string(),
    })
}

const END_TURN: &str = r#"{"content":[{"type":"text","text":"hello"}],"stop_reason":"end_turn"}"#;

fn client(transport: MockTransport, retries: u32) -> AnthropicModelClient<MockTransport> {
    AnthropicModelClient::builder("test-key", "some-model", transport)
        .max_retries(retries)
        .retry_base_delay(Duration::ZERO)
        .build()
        .unwrap()
}

fn request() -> ModelRequest {
    ModelRequest::new("sys", vec![Message::user("hi")], vec![], 128)
}

#[test]
fn sends_the_expected_url_headers_and_body() {
    let transport = MockTransport::with(vec![reply(200, END_TURN)]);
    let response = client(transport.clone(), 0).send(&request()).unwrap();
    assert_eq!(response.text(), "hello");

    let seen = transport.seen.lock().unwrap();
    assert_eq!(seen[0].url, "https://api.anthropic.com/v1/messages");
    let header = |name: &str| {
        seen[0]
            .headers
            .iter()
            .find(|(k, _)| k == name)
            .map(|(_, v)| v.as_str())
    };
    assert_eq!(header("x-api-key"), Some("test-key"));
    assert_eq!(header("anthropic-version"), Some("2023-06-01"));
    assert_eq!(header("content-type"), Some("application/json"));
    let body = Json::parse(&seen[0].body).unwrap();
    assert_eq!(body.get("model").and_then(Json::as_str), Some("some-model"));
}

#[test]
fn retries_a_429_and_then_succeeds() {
    let transport = MockTransport::with(vec![
        reply(429, r#"{"error":"rate limited"}"#),
        reply(200, END_TURN),
    ]);
    let response = client(transport.clone(), 2).send(&request()).unwrap();
    assert_eq!(response.text(), "hello");
    assert_eq!(transport.calls(), 2);
}

#[test]
fn retries_a_server_error_and_a_network_error() {
    let transport = MockTransport::with(vec![
        reply(529, "overloaded"),
        Err(TransportError("connection reset".to_string())),
        reply(200, END_TURN),
    ]);
    assert!(client(transport.clone(), 2).send(&request()).is_ok());
    assert_eq!(transport.calls(), 3);
}

#[test]
fn gives_up_after_the_retries_are_used() {
    let transport = MockTransport::with(vec![reply(500, "a"), reply(500, "b"), reply(500, "c")]);
    let error = client(transport.clone(), 2).send(&request()).unwrap_err();
    assert!(error.message.contains("HTTP 500"));
    assert_eq!(transport.calls(), 3);
}

#[test]
fn does_not_retry_a_client_error() {
    let transport = MockTransport::with(vec![
        reply(400, r#"{"error":"bad request"}"#),
        reply(200, END_TURN),
    ]);
    let error = client(transport.clone(), 2).send(&request()).unwrap_err();
    assert!(error.message.contains("HTTP 400"));
    assert_eq!(transport.calls(), 1);
}

#[test]
fn rejects_an_unknown_stop_reason() {
    let transport = MockTransport::with(vec![reply(
        200,
        r#"{"content":[],"stop_reason":"surprise"}"#,
    )]);
    let error = client(transport, 0).send(&request()).unwrap_err();
    assert!(error.message.contains("Unknown stop_reason"));
}

#[test]
fn rejects_a_blank_key_or_model() {
    let blank_key = AnthropicModelClient::builder("  ", "m", MockTransport::default()).build();
    let blank_model = AnthropicModelClient::builder("k", "", MockTransport::default()).build();
    assert!(blank_key.is_err() && blank_model.is_err());
}

#[test]
fn an_agent_runs_a_full_tool_loop_through_the_adapter() {
    let tool_use = r#"{"content":[{"type":"tool_use","id":"t1","name":"echo","input":{"text":"hi"}}],
        "stop_reason":"tool_use"}"#;
    let transport = MockTransport::with(vec![reply(200, tool_use), reply(200, END_TURN)]);

    let echo = FunctionTool::new(
        ToolDefinition::new(
            "echo",
            "Echo",
            InputSchema::object().string("text", "Text", true).build(),
        )
        .unwrap(),
        |input| format!("echo:{}", input["text"].as_str().unwrap_or("")),
    );
    let agent = Agent::builder("real")
        .model(client(transport.clone(), 0))
        .instructions(Instructions::for_role("test"))
        .tool(echo)
        .build()
        .unwrap();

    let result = agent.run("go");
    assert!(result.completed() && result.iterations == 2);
    let seen = transport.seen.lock().unwrap();
    assert!(seen[1].body.contains("tool_result") && seen[1].body.contains("echo:hi"));
}
