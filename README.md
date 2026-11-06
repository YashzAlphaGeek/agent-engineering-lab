# Catalyst Agent Architecture Reference

A generic, tool-agnostic reference for building agents with the Claude loop model. It describes the components, how they connect and the rules that keep an agent reliable. It deliberately uses no domain examples: replace `Tool A`, `Subagent A` etc. with your own.

## Setup

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -e .
pip list
```

---

## 1. What an Agent Is

An agent takes a **goal**, repeatedly **decides** the next action, **acts** through tools, **observes** the result and stops when the goal is met.

```text
Goal -> Decide -> Act (tool) -> Observe -> Evaluate -> repeat or finish
```

---

## 2. Core Components (single agent)

```mermaid
classDiagram

    class Agent {
        +run(goal)
    }

    class AgentLoop {
        +run(state)
    }

    class AgentState {
        +goal
        +result
        +history
        +iterationCount
        +stopReason
        +finalResponse
        +completed
    }

    class DecisionMaker {
        <<role>>
        decides the next action
    }

    class Planner {
        +createPlan(state)
    }

    class LLMClient {
        <<interface>>
        +nextResponse(messages, tools)
    }

    class Executor {
        +execute(tool, input)
    }

    class Evaluator {
        +evaluate(result)
    }

    class ToolRegistry {
        +register(tool)
        +get(name)
    }

    class BaseTool {
        <<interface>>
        +definition()
        +execute(input)
    }

    class ToolDefinition {
        +name
        +description
        +inputSchema
    }

    Agent --> AgentLoop
    AgentLoop --> AgentState
    AgentLoop --> DecisionMaker
    AgentLoop --> ToolRegistry
    AgentLoop --> Executor
    AgentLoop --> Evaluator
    DecisionMaker <|.. Planner : rule based
    DecisionMaker <|.. LLMClient : model based
    ToolRegistry --> BaseTool
    BaseTool --> ToolDefinition
    Executor --> BaseTool
```

| Component | Responsibility |
|-----------|----------------|
| `Agent` | Entry point: `run(goal)` creates the state and starts the loop |
| `AgentLoop` | Repeats decide -> act -> observe until finished or capped |
| `AgentState` | Memory of the run (goal, history, counters, stop reason, final answer) |
| `Planner` | Rule-based decision maker (no model) |
| `LLMClient` | Model-based decision maker (replaceable by a mock) |
| `Executor` | Runs the selected tool; does no thinking |
| `Evaluator` | Decides whether to continue (rule based) |
| `ToolRegistry` | Lookup of the tools the agent is allowed to use (a safety boundary) |
| `BaseTool` / `ToolDefinition` | Tool behaviour and the metadata (name, description, input schema) the model reads |

**Principle:** only the decision maker changes between a rule-based and a model-based agent. State, loop, registry and tools stay the same.

---

## 3. Rule-Based Loop (Planner + Evaluator)

```mermaid
flowchart TD

    G[Goal] --> A[Agent]
    A --> L[AgentLoop]
    L --> P[Planner]
    P --> R[ToolRegistry]
    R --> T[Tool]
    T --> E[Executor]
    E --> RES[Result]
    RES --> EV[Evaluator]
    EV --> S[AgentState]
    S --> C{Completed?}
    C -->|No| P
    C -->|Yes| F[Final Response]
```

---

## 4. Claude Tool Loop (model-driven)

The model decides. The loop reacts to the **stop reason**.

```mermaid
flowchart TD

    G[Goal] --> L[AgentLoop]
    L --> M[LLMClient]
    M --> CR[ClaudeResponse]
    CR --> D{StopReason}

    D -->|TOOL_USE| TC[ToolCall]
    TC --> R[ToolRegistry]
    R --> T[Tool.execute]
    T --> TR[ToolResult]
    TR --> S[AgentState: append to history]
    S --> L

    D -->|END_TURN| F[Final Response]
```

### Loop in pseudocode

```text
while not completed:
    if iterationCount >= MAX_ITERATIONS: warn, break      # safety cap only
    iterationCount++
    response = client.nextResponse(history, tools)
    state.stopReason = response.stopReason

    if response.stopReason == END_TURN:
        state.finalResponse = response.text
        state.completed = true
        break

    if response.stopReason == TOOL_USE:
        tool   = registry.get(response.toolCall.name)
        output = tool.execute(response.toolCall.input)
        history.append(ToolResult(response.toolCall.id, output))
```

### Differences between the two loops

| | Rule-based loop | Model-driven loop |
|---|---|---|
| Who decides the next step | `Planner` | The model, via `ClaudeResponse` |
| Continue / stop signal | `Evaluator` | `StopReason` (`TOOL_USE` / `END_TURN`) |
| Tool request | Planner picks a tool name | `ToolCall` |
| Tool output | `Result` | `ToolResult` |
| Safety cap | optional | `MAX_ITERATIONS` |

### Data types

```mermaid
classDiagram

    class ClaudeResponse {
        +stopReason
        +toolCall
        +text
    }

    class StopReason {
        <<enum>>
        TOOL_USE
        END_TURN
    }

    class ToolCall {
        +id
        +toolName
        +input
    }

    class ToolResult {
        +toolCallId
        +content
    }

    ClaudeResponse --> StopReason
    ClaudeResponse --> ToolCall
    ToolCall .. ToolResult : linked by id
```

### Sequential tool calls

```text
Iteration 1: TOOL_USE (Tool A)
Iteration 2: TOOL_USE (Tool B)   <- may depend on Tool A's result
Iteration 3: END_TURN

iterationCount = 3, history size = 2, completed = true
```

Iterations = tool calls + 1 (the final `END_TURN` call is counted but runs no tool).

---

## 5. Multi-Agent Orchestration (hub-and-spoke)

```mermaid
flowchart TD

    G[Goal] --> C[Coordinator]

    C -->|1 decompose| ST[SubTopics]
    ST -->|2 delegate with ResearchContext| SA1[Subagent A]
    ST --> SA2[Subagent B]
    ST --> SA3[Subagent C]

    SA1 -->|Finding + Metadata| C
    SA2 -->|Finding + Metadata| C
    SA3 -->|Finding + Metadata| C

    C -->|3 evaluate| CR[CoverageReport]
    C -->|4 aggregate| RP[ResearchReport]
    CR --> RP
    RP --> F[Final Response]
```

**Key rule:** subagents never talk to each other. All communication and context flows through the coordinator.

| Step | Coordinator action |
|------|--------------------|
| Decompose | Split the goal into sub-topics |
| Delegate | Build a context per sub-topic and call the matching subagent |
| Evaluate coverage | Compare planned sub-topics with findings; list covered, gaps, completeness |
| Refine | Re-delegate for gaps (planned, not required for a first version) |
| Aggregate | Combine findings into one report |

### Multi-agent data model

```mermaid
classDiagram

    class Coordinator {
        +research(goal)
        -decompose(goal)
        -delegate(goal, subTopics)
        -evaluateCoverage(subTopics, findings)
        -aggregate(goal, findings)
    }

    class Subagent {
        <<interface>>
        +research(context)
    }

    class ContextBuilder {
        +build(goal, subTopic, priorFindings)
    }

    class ResearchContext {
        +originalGoal
        +assignedSubTopic
        +expectedOutput
        +priorFindings
    }

    class SubTopic {
        +name
    }

    class ResearchFinding {
        +claim
        +analysis
        +metadata
    }

    class ResearchMetadata {
        +sourceUrl
        +documentName
        +pageNumber
        +confidence
        +retrievedBy
    }

    class ResearchReport {
        +topic
        +findings
    }

    class CoverageReport {
        +covered
        +gaps
        +completeness
    }

    Coordinator --> ContextBuilder
    Coordinator --> Subagent
    Coordinator --> SubTopic
    Coordinator --> CoverageReport
    Coordinator --> ResearchReport
    ContextBuilder --> ResearchContext
    Subagent --> ResearchContext : receives
    Subagent --> ResearchFinding : returns
    ResearchFinding --> ResearchMetadata
    ResearchReport --> ResearchFinding
```

### Context passing

A subagent has **isolated context**: it knows only what is in its `ResearchContext`. Always pass:

```text
originalGoal      the overall objective
assignedSubTopic  this subagent's slice
expectedOutput    the format to return
priorFindings     earlier findings it needs (flows through the coordinator only)
```

### Provenance

Every finding carries `ResearchMetadata` (source, document, page, confidence, which agent retrieved it). Unknown fields stay `null`; never invent them. This allows conflicts between sources to be annotated instead of silently merged.

---

## 6. Phases

| Phase | Focus | Decision maker |
|-------|-------|----------------|
| 1 | Single agent architecture: Agent, AgentState, Planner, Executor, Evaluator, AgentLoop, tools | `Planner` (rules) |
| 2 | Claude-style loop: `StopReason`, `ToolCall`, `ToolResult`, `ClaudeResponse`, mock client | Mock `LLMClient` |
| 2.5 | Sequential tool calls: `TOOL_USE` -> `TOOL_USE` -> `END_TURN` | Mock `LLMClient` |
| 3 | Multi-agent: coordinator, subagents, decomposition, delegation, aggregation, coverage | Coordinator |
| 4 | Context passing and provenance: `ResearchContext`, `ContextBuilder`, `ResearchMetadata` | Coordinator passes context |
| Next | Replace the mock with the real SDK (`client.messages.create()`) | Real model |

Phase documents: `catalyst-java/Catalyst_Phase1_Agent_Architecture.md`, `Catalyst_Phase2_Claude_Tool_Loop.md`, `Catalyst_Phase3_MultiAgent_Orchestration.md`.
Study guide: `catalyst-java/go-to/Catalyst_Prep_Guide.html`.

---

## 7. Rules That Keep an Agent Reliable

1. **`stop_reason` is the loop signal.** `tool_use` -> run the tool and continue. `end_turn` -> finish.
2. **Never** decide completion by parsing the model's text, by checking only the first content block, or by an iteration cap.
3. **The iteration cap is a safety bound** against runaway loops, not the normal exit. Warn when it trips.
4. **Append every tool result to the history** before the next model call. Shrink tool output if it is too large; do not drop it.
5. **Only registered tools can run.** The registry is a safety boundary. Unknown tool names must fail safely.
6. **Tool descriptions drive selection.** State inputs, outputs and "do not use for X" boundaries. Give each agent a small, relevant tool set.
7. **Return structured errors** from tools: category (transient / validation / business / permission), retryable flag, message. Distinguish a valid empty result from a failure.
8. **Enforce hard policies in code** (hooks, prerequisite gates), not in prompts.
9. **Subagents get explicit context.** They do not inherit the coordinator's conversation.
10. **Independent subtasks can run in parallel** (several delegations in one step).
11. **Coverage metrics must be able to fail.** Compare planned topics with findings that contain real evidence.
12. **Keep claims with their sources** and annotate conflicts.
13. **Escalate with a structured summary** (issue, actions taken, facts, recommended next step), and honor explicit requests for a human.
14. **Use a mock client for tests** so loop logic is deterministic and needs no keys, credits or network.

---

## 8. Claude Concept Mapping

| Claude concept | Component |
|----------------|-----------|
| Agent runtime / `while(true)` | `Agent` / `AgentLoop` |
| `messages[]` | `AgentState.history` |
| `tools[]` | `ToolRegistry` + `ToolDefinition` |
| `client.messages.create()` | `LLMClient` (mock first) |
| `stop_reason` | `StopReason` |
| `tool_use` block | `ToolCall` |
| `tool_result` block | `ToolResult` |
| `end_turn` | `StopReason.END_TURN` |
| Safety bound | `MAX_ITERATIONS` |
| Coordinator / subagents (Task tool) | `Coordinator` / `Subagent` |
| Task decomposition | `decompose()` |
| Subagent context passing | `ResearchContext` |
| Provenance | `ResearchMetadata` |
| Coverage evaluation | `CoverageReport` |

---

## 9. Testing Strategy

| Area | What to verify |
|------|----------------|
| Decision maker | Goal -> expected action |
| Registry | Register, look up, unknown name handled |
| Loop | Iterations counted, completes on `END_TURN`, cap enforced |
| Mock client | Scripted responses come out in order |
| Sequential calls | `TOOL_USE`, `TOOL_USE`, `END_TURN`: iterations 3, history 2 |
| Coordinator | Decompose, delegate, coverage, aggregate |
| Subagents | Return a finding for the context they receive |
| Context | Context built correctly and passed unchanged |
| Isolation | A subagent uses only the context it was given |
| Metadata | Provenance survives into the finding |

---

## 10. Module Layout (generic)

```text
project/
    core/        Agent, AgentState, Planner, Executor, Evaluator, AgentLoop
    llm/         StopReason, ToolCall, ToolResult, ClaudeResponse, LLMClient (mock, later real)
    tools/       BaseTool, ToolDefinition, ToolRegistry, <your tools>
    agents/      Coordinator, <your subagents>
    context/     ContextBuilder
    model/       SubTopic, ResearchContext, ResearchFinding, ResearchMetadata,
                 ResearchReport, CoverageReport
    phases/      runnable demos per phase
    tests/       one test group per area above
```

---

## 11. Learning Objective (1.1 Agentic Loops and beyond)

```text
Goal -> Plan -> Select Tool -> Execute Tool -> Observe Result -> Evaluate -> Respond
```

Everything later in the Claude Certified Architect material builds on: Agent, AgentState, decision maker, Executor, Evaluator, AgentLoop, ToolRegistry, StopReason, Coordinator, context passing and provenance.
