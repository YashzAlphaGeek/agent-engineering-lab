# golden-agent (Java)

A small, framework-free Java library for building agents from the five core elements:
**model, instructions, tools, loop and state**, plus the guard layer (gates, human approval, result filters)
and sub agents. Plain Java, no dependencies, no build tool required.

Use it the way you would use an agent framework: define a few small pieces, build an `Agent`, call `run`.

## Requirements

- JDK 21 or newer. It is compiled with `--release 21`, so it runs unchanged on 21, 23, 25 and later.
- Verified here with JDK 23. Nothing in it uses preview features.

## Quick start

```java
Agent agent = Agent.builder("support")
        .model(modelClient)                                              // 1. model
        .instructions(Instructions.forRole("You are a support agent.")   // 2. instructions
                .withRule("Verify the customer before changing an account."))
        .tools(new GetCustomerTool(), new RefundTool())                  // 3. tools
        .gate(Gates.prerequisites(Map.of("process_refund", Set.of("get_customer"))))
        .gate(Gates.requireApproval(Set.of("process_refund"), "refunds need approval"))
        .approver((call, reason) -> askAPerson(call, reason))
        .maxIterations(10)                                               // 4. loop limits
        .build();

AgentResult result = agent.run("Refund order O-1 for customer C-1");     // 5. fresh state per run
System.out.println(result.status() + ": " + result.text());
```

Run `build.ps1` (Windows) or `build.sh` (macOS and Linux) to compile, run 25 self-checks, run the demo and
package `dist/golden-agent-1.0.0.jar`.

## Where each element lives

| Element | Types | Package |
|---|---|---|
| 1. Model | `ModelClient`, `ModelRequest`, `ModelResponse`, `StopReason`, `ScriptedModelClient` | `golden.agent.model` |
| 2. Instructions | `Instructions` | `golden.agent.instructions` |
| 3. Tools | `Tool`, `TypedTool<I,O>`, `FunctionTool`, `ToolDefinition`, `InputSchema`, `ToolRegistry` | `golden.agent.tool` |
| 4. Loop | `AgentLoop`, `LoopConfig`, `AgentResult` | `golden.agent.loop` |
| 5. State | `AgentState`, `RunStatus` | `golden.agent.state` |
| Guard layer | `Gate`, `Gates`, `Decision`, `HumanApprover`, `ResultFilter` | `golden.agent.guard` |
| Composition | `Agent` (the entry point), `DelegationTool` | `golden.agent.composition` |
| Real provider | `AnthropicModelClient`, `Json` | `golden.agent.provider` |

## Extension points

Everything is an interface or a small value type. To extend the library you add code, you do not change it.

| To add a... | Implement or call |
|---|---|
| Model provider | `ModelClient` (one method) |
| Tool | `Tool`, or subclass `TypedTool<I,O>`, or `FunctionTool.of(...)` |
| Rule that must never be broken | `Gate` (returns allow, deny or ask a human) |
| Human approval channel | `HumanApprover` (console, ticket, chat, web form) |
| Output trimming or redaction | `ResultFilter` |
| Sub agent | `Agent.builder(...).subAgent(other)` |

## What the loop guarantees

- Branches only on `stop_reason`. It never parses text such as "I'm done".
- `end_turn` finishes. `tool_use` runs the tools and loops. Any other stop reason ends the run as `NOT_FINISHED`.
- The iteration cap is a guard rail and ends the run as `LIMIT_REACHED`, never as a normal finish.
- Every tool call passes the gate first. A denied call returns an error result to the model and does not run.
- An ask-human decision fails closed: with no approver, it is denied.
- Unknown tools, bad arguments and tool failures become error results the model can read. Internals are not leaked.
- The full message history is sent on every call. Each `run` creates a new isolated state.
- A sub agent sees only the task and context the coordinator passes. A failed sub agent returns a structured error.

## Using it from your own project

Add `dist/golden-agent-1.0.0.jar` to the classpath:

```
javac -cp golden-agent-1.0.0.jar MyApp.java
java  -cp golden-agent-1.0.0.jar;. MyApp        (use : instead of ; on macOS and Linux)
```

With Maven, `mvn install` in this folder puts it in your local repository (`pom.xml` is provided and has no
dependencies). Then depend on `io.golden:golden-agent:1.0.0`.

The jar declares the automatic module name `golden.agent`.

## Using a real model

`ScriptedModelClient` replays a fixed script, so the demo and checks are deterministic and need no key.
For a real model use the reference adapter:

```java
ModelClient client = AnthropicModelClient.fromEnvironment("<model id>");   // reads ANTHROPIC_API_KEY
```

Its request and response mapping is covered by offline checks, but it has **not been run against the live API
here**, so test it with your own key first. You choose the model id. Any other provider only needs one
`ModelClient` implementation.

## Not included on purpose

Streaming, long-term memory, context compaction, parallel tool execution, parallel sub agents, persistence and
tracing. Each one fits behind the existing extension points (a `ModelClient` wrapper, a `ResultFilter`, a
`Gate`, or a custom `Agent` runner) and is easy to add when needed.

## Layout

```
src/main/java   the library (this is what the jar contains)
src/test/java   SelfCheck, plain Java checks with no test framework
src/demo/java   Demo, two runnable examples
```
