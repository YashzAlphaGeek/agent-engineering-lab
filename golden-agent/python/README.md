# golden-agent (Python)

A small, framework-free Python library for building agents from the five core elements:
**model, instructions, tools, loop and state**, plus the guard layer (gates, human approval, result filters)
and sub agents. Standard library only, no dependencies.

It is the Python version of the Java library in `../java` and behaves the same way. Use it the way you would use
an agent framework: define a few small pieces, build an `Agent`, call `run`.

## Requirements

- Python 3.10 or newer (it uses structural pattern matching and `X | None` types).
- Verified here on Python 3.12.6 only.

## Quick start

```python
from golden_agent import Agent, Gates, Instructions

agent = (
    Agent.builder("support")
    .model(model_client)                                                 # 1. model
    .instructions(Instructions.for_role("You are a support agent.")      # 2. instructions
                  .with_rule("Verify the customer before changing an account."))
    .tools(GetCustomerTool(), RefundTool())                              # 3. tools
    .gate(Gates.prerequisites({"process_refund": {"get_customer"}}))
    .gate(Gates.require_approval({"process_refund"}, "refunds need approval"))
    .approver(lambda call, reason: ask_a_person(call, reason))
    .max_iterations(10)                                                  # 4. loop limits
    .build()
)

result = agent.run("Refund order O-1 for customer C-1")                  # 5. fresh state per run
print(result.status.name, result.text)
```

Keyword construction works too:

```python
agent = Agent("support", model=model_client, instructions=Instructions.for_role("..."),
              tools=[GetCustomerTool()], sub_agents=[researcher], max_iterations=10)
```

Run `build.ps1` (Windows) or `build.sh` (macOS and Linux) to run the 34 tests, run the demo and build a wheel.

## Where each element lives

| Element | Names | Module |
|---|---|---|
| 1. Model | `ModelClient`, `ModelRequest`, `ModelResponse`, `StopReason`, `ScriptedModelClient` | `golden_agent.model` |
| 2. Instructions | `Instructions` | `golden_agent.instructions` |
| 3. Tools | `Tool`, `TypedTool[I, O]`, `FunctionTool`, `ToolDefinition`, `InputSchema`, `ToolRegistry` | `golden_agent.tool` |
| 4. Loop | `AgentLoop`, `LoopConfig`, `AgentResult` | `golden_agent.loop` |
| 5. State | `AgentState`, `RunStatus` | `golden_agent.state` |
| Guard layer | `Gate`, `Gates`, `Decision` (`Allow`, `Deny`, `AskHuman`), `HumanApprover`, `ResultFilter` | `golden_agent.guard` |
| Composition | `Agent` (the entry point), `AgentBuilder`, `DelegationTool` | `golden_agent.composition` |
| Real provider | `AnthropicModelClient` | `golden_agent.provider` |

Everything public is also importable from the top-level `golden_agent` package.

## Extension points

Gates, approvers and filters are plain callables, and models and tools are anything with the right methods
(structural typing). To extend the library you add code, you do not change it.

| To add a... | Provide |
|---|---|
| Model provider | An object with `send(request) -> ModelResponse`, or just a function |
| Tool | An object with `definition()` and `execute(input)`, or subclass `TypedTool`, or `FunctionTool.of(...)` |
| Rule that must never be broken | A `Gate`: `(tool_use, state) -> Decision` |
| Human approval channel | A `HumanApprover`: `(tool_use, reason) -> bool` |
| Output trimming or redaction | A `ResultFilter`: `(tool_use, output) -> ToolOutput` |
| Sub agent | `Agent.builder(...).sub_agent(other)` |

## What the loop guarantees

- Branches only on `stop_reason`. It never parses text such as "I'm done".
- `END_TURN` finishes. `TOOL_USE` runs the tools and loops. Any other stop reason ends the run as `NOT_FINISHED`.
- The iteration cap is a guard rail and ends the run as `LIMIT_REACHED`, never as a normal finish.
- Every tool call passes the gate first. A denied call returns an error result to the model and does not run.
- An ask-human decision fails closed: with no approver, it is denied.
- Unknown tools, bad arguments and tool failures become error results the model can read. Internals are not leaked.
- The full message history is sent on every call. Each `run` creates a new isolated state.
- A sub agent sees only the task and context the coordinator passes. A failed sub agent returns a structured error.

## Using it from your own project

Build the wheel with `build.ps1` or `build.sh`, then install it, or install straight from the folder:

```
pip install dist/golden_agent-1.0.0-py3-none-any.whl
pip install .            # from this folder
pip install -e .         # editable, for working on the library
```

## Using a real model

`ScriptedModelClient` replays a fixed script, so the demo and tests are deterministic and need no key.
For a real model use the reference adapter:

```python
from golden_agent import AnthropicModelClient

client = AnthropicModelClient.from_environment("<model id>")   # reads ANTHROPIC_API_KEY
```

Its request and response mapping and its retry behaviour are covered by offline tests that use a fake transport,
but it has **not been run against the live API here**, so test it with your own key first. You choose the model id.
Any other provider only needs one object with a `send` method.

## Not included on purpose

Streaming, long-term memory, context compaction, parallel tool execution, parallel sub agents, persistence and
tracing. Each one fits behind the existing extension points and is easy to add when needed.

## Layout

```
src/golden_agent/   the library (this is what the wheel contains)
tests/              unittest tests, 25 ported from the Java checks plus 9 for the Anthropic adapter
examples/demo.py    two runnable examples
```
