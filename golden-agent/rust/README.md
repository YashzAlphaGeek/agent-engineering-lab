# golden-agent (Rust)

A small, framework-free Rust library for building agents from the five core elements:
**model, instructions, tools, loop and state**, plus the guard layer (gates, human approval, result filters)
and sub agents. It uses only the Rust standard library, with zero dependencies.

Use it the way you would use an agent framework: define a few small pieces, build an `Agent`, call `run`.
It mirrors the Java library in `../java` in design, behaviour and guarantees.

## Requirements

- Rust 1.85 or newer (edition 2024).
- Verified here with rustc 1.95.0 only. The crate forbids `unsafe` code.

## Quick start

```rust
use std::sync::Arc;
use golden_agent::prelude::*;

let agent = Agent::builder("support")
    .model(model_client)                                              // 1. model
    .instructions(
        Instructions::for_role("You are a support agent.")            // 2. instructions
            .with_rule("Verify the customer before changing an account."),
    )
    .tool(get_customer_tool())                                        // 3. tools
    .tool(refund_tool())
    .gate(Gates::prerequisites(&[("process_refund", &["get_customer"])]))
    .gate(Gates::require_approval(&["process_refund"], "refunds need approval"))
    .approver(HumanApprover::new(|call, reason| ask_a_person(call, reason)))
    .max_iterations(10)                                               // 4. loop limits
    .build()?;                                                        // validates and returns a Result

let result = agent.run("Refund order O-1 for customer C-1");          // 5. fresh state per run
println!("{}: {}", result.status, result.text);
```

Run the bundled demo with `cargo run --example demo`, and everything (format check, lints, tests, demo,
package) with `build.ps1` on Windows or `build.sh` on macOS and Linux.

## Where each element lives

| Element | Types | Module |
|---|---|---|
| 1. Model | `ModelClient`, `ModelRequest`, `ModelResponse`, `StopReason`, `ContentBlock`, `Message`, `ScriptedModelClient` | `model` |
| 2. Instructions | `Instructions` | `instructions` |
| 3. Tools | `Tool`, `TypedTool<I, O>`, `FunctionTool`, `ToolDefinition`, `InputSchema`, `ToolRegistry` | `tool` |
| 4. Loop | `AgentLoop`, `LoopConfig`, `AgentResult` | `agent_loop` |
| 5. State | `AgentState`, `RunStatus` | `state` |
| Guard layer | `Gate`, `Gates`, `Decision`, `HumanApprover`, `ResultFilter` | `guard` |
| Composition | `Agent` (the entry point), `AgentBuilder`, `DelegationTool` | `agent` |
| Real provider | `AnthropicModelClient`, `HttpTransport` | `provider::anthropic` |
| JSON | `Json` | `json` |

## Extension points

Everything is a trait or a small value type. To extend the library you add code, you do not change it.

| To add a... | Implement or call |
|---|---|
| Model provider | `ModelClient` (one method, and any matching closure works) |
| Tool | `Tool`, or `TypedTool::new(...)`, or `FunctionTool::new(...)` |
| Rule that must never be broken | `Gate::new(|call, state| ...)` returning a `Decision` |
| Human approval channel | `HumanApprover::new(|call, reason| ...)` |
| Output trimming or redaction | `ResultFilter::new(|call, output| ...)` |
| Sub agent | `Agent::builder(...).sub_agent(Arc::new(other))` |

## What the loop guarantees

- Branches only on `stop_reason`. It never parses text such as "I'm done".
- `EndTurn` finishes. `ToolUse` gates and runs the tools, then loops. Any other stop reason ends the run as
  `NotFinished`.
- The iteration cap is a guard rail and ends the run as `LimitReached`, never as a normal finish.
- Every tool call passes the gate first. A denied call returns an error result to the model and does not run.
- An ask-human decision fails closed: with no approver, it is denied.
- Unknown tools, bad arguments and tool failures become error results the model can read. A panic inside a tool
  is caught at the loop boundary and reported without its message, so internals are not leaked.
- All tool results for one assistant turn go back together in a single user message.
- The full message history is sent on every call. Each `run` creates a new isolated state.
- A sub agent sees only the task and context the coordinator passes. A failed sub agent returns a structured
  `SUB_AGENT_FAILURE` error.
- `Agent` is `Send + Sync` and holds no run state, so one instance can be shared and run from many threads.

## Using it from your own project

Add it as a path or git dependency:

```toml
[dependencies]
golden-agent = { path = "../golden-agent/rust" }
```

`cargo package` also produces a `.crate` file you can use for a private registry. The crate has no
dependencies, so it builds offline.

## Using a real model

`ScriptedModelClient` replays a fixed script, so the demo and tests are deterministic and need no key.

The Rust standard library has no HTTPS client, so `AnthropicModelClient` is generic over an `HttpTransport`
that **you supply**. The trait has one method, `post(url, headers, body, timeout)`, and is usually a thin wrapper
over the HTTP crate your project already uses:

```rust
use std::time::Duration;
use golden_agent::provider::anthropic::{AnthropicModelClient, HttpResponse, HttpTransport, TransportError};

struct MyTransport; // wrap reqwest, ureq, hyper or anything else

impl HttpTransport for MyTransport {
    fn post(&self, url: &str, headers: &[(&str, &str)], body: &str, timeout: Duration)
        -> Result<HttpResponse, TransportError> {
        // send an HTTPS POST and return the status code and body text
        todo!()
    }
}

let client = AnthropicModelClient::from_environment("<model id>", MyTransport)?; // reads ANTHROPIC_API_KEY
```

Request building, response parsing, retries with exponential backoff (rate limits and server errors) and strict
`stop_reason` handling are covered by offline tests with a mock transport. The adapter has **not been run against
the live API here**, so test it with your own key first. You choose the model id. Any other provider only needs
one `ModelClient` implementation.

## Not included on purpose

Streaming, long-term memory, context compaction, parallel tool execution, parallel sub agents, persistence and
tracing. Each one fits behind the existing extension points and is easy to add when needed.

## Layout

```
src/            the library
tests/          integration tests: 25 ported checks, adapter tests, concurrency tests
examples/demo.rs   two runnable examples
```
