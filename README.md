# Catalyst Architecture

## Setup

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -e .
pip list
```

---

## Phases

| Phase | Focus | Decision maker |
|-------|-------|----------------|
| 1 | Agent architecture (Agent, AgentState, Planner, Executor, Evaluator, AgentLoop, tools) | `Planner` (rules) |
| 2 | Claude-style tool loop (`StopReason`, `ToolCall`, `ToolResult`, `ClaudeResponse`, `MockClaudeClient`, `AgentLoopV2`) | `MockClaudeClient` |
| 2.5 | Sequential tool calls: `TOOL_USE` -> `TOOL_USE` -> `END_TURN` | `MockClaudeClient` |
| 3 (next) | Replace `MockClaudeClient` with the Anthropic SDK (`client.messages.create()`) | Real Claude |

Details: `catalyst-java/Catalyst_Phase1_Agent_Architecture.md`, `catalyst-java/Catalyst_Phase2_Claude_Tool_Loop.md`.
Study guide (single HTML): `catalyst-java/go-to/Catalyst_Prep_Guide.html`.

---

## Agent Architecture (Phase 1)

```mermaid
flowchart TD

    U[User Goal]

    U --> A[Agent]

    A --> L[AgentLoop]

    L --> P[Planner]

    P --> T[Tool Registry]

    T --> JT[JobSearchTool]
    T --> RT[ResumeAnalyzerTool]

    JT --> E[Executor]
    RT --> E

    E --> R[Result]

    R --> EV[Evaluator]

    EV --> S[AgentState]

    S --> C{Completed?}

    C -->|No| P

    C -->|Yes| F[Final Response]
```

---

## Claude Tool Loop (Phase 2 / 2.5)

```mermaid
flowchart TD

    G[User Goal] --> L2[AgentLoopV2]

    L2 --> M[MockClaudeClient]

    M --> CR[ClaudeResponse]

    CR --> D{StopReason}

    D -->|TOOL_USE| TC[ToolCall]

    TC --> TR[ToolRegistry]

    TR --> TL[Tool.execute]

    TL --> RES[ToolResult]

    RES --> ST[AgentState]

    ST --> L2

    D -->|END_TURN| FR[Final Response]
```

`MAX_ITERATIONS = 20` is only a safety cap. `END_TURN` is the normal exit.

---

## Core Components

```mermaid
classDiagram

    class Agent {
        +run(goal)
    }

    class AgentLoop {
        +run(state)
    }

    class AgentLoopV2 {
        <<Phase 2 - Claude style>>
        -MAX_ITERATIONS = 20
        -client : MockClaudeClient
        -registry : ToolRegistry
        +run(state)
    }
    note for AgentLoopV2 "DIFFERENT FROM AgentLoop:<br/>- NO Planner / Evaluator: Claude decides<br/>- Driven by StopReason<br/>   TOOL_USE = run tool, continue<br/>   END_TURN = completed, break<br/>- Tool requested via ToolCall<br/>- Output stored as ToolResult<br/>- MAX_ITERATIONS = safety cap only"

    class AgentState {
        +goal
        +result
        +completed
        +iterationCount
        +history
    }

    class Planner {
        +createPlan(state)
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

    class MockClaudeClient {
        +nextResponse()
    }

    class ClaudeResponse {
        +stopReason
        +toolCall
    }

    class StopReason {
        <<enum>>
        TOOL_USE
        END_TURN
    }

    class ToolCall {
        +toolName
        +input
    }

    class ToolResult {
        +output
    }

    Agent --> AgentLoop
    AgentLoop --> AgentState
    AgentLoop --> Planner
    AgentLoop --> Executor
    AgentLoop --> Evaluator
    AgentLoop --> ToolRegistry
    ToolRegistry --> BaseTool
    BaseTool --> ToolDefinition
    AgentLoopV2 --> MockClaudeClient
    AgentLoopV2 --> AgentState
    AgentLoopV2 --> ToolRegistry
    MockClaudeClient --> ClaudeResponse
    ClaudeResponse --> StopReason
    ClaudeResponse --> ToolCall
    AgentLoopV2 --> ToolResult

    %% Highlight the new Phase 2 core parts
    style AgentLoopV2 fill:#fff2de,stroke:#d97706,stroke-width:3px
    style MockClaudeClient fill:#fff2de,stroke:#d97706,stroke-width:3px
    style ClaudeResponse fill:#fff2de,stroke:#d97706,stroke-width:2px
    style StopReason fill:#fff2de,stroke:#d97706,stroke-width:2px
    style ToolCall fill:#fff2de,stroke:#d97706,stroke-width:2px
    style ToolResult fill:#fff2de,stroke:#d97706,stroke-width:2px
```

**Legend:** orange = new in Phase 2. `AgentLoop` (Phase 1) uses `Planner` + `Evaluator` to decide; `AgentLoopV2` hands the decision to Claude (`MockClaudeClient` for now) and reacts to `StopReason`.

| | AgentLoop (Phase 1) | AgentLoopV2 (Phase 2) |
|---|---|---|
| Who decides next step | `Planner` | Claude, via `ClaudeResponse` |
| Continue / stop signal | `Evaluator` (`result != null`) | `StopReason` (`TOOL_USE` / `END_TURN`) |
| Tool request | Planner picks tool name | `ToolCall` |
| Tool output | `Result` | `ToolResult` |
| Safety cap | none | `MAX_ITERATIONS = 20` |

---

## Request Flow (Phase 1)

```mermaid
sequenceDiagram

    participant User
    participant Agent
    participant Planner
    participant Registry
    participant JobSearchTool
    participant Executor
    participant Evaluator

    User->>Agent: Find Python jobs in Munich

    Agent->>Planner: Create Plan

    Planner-->>Agent: job_search

    Agent->>Registry: Get Tool

    Registry-->>Agent: JobSearchTool

    Agent->>Executor: Execute

    Executor->>JobSearchTool: search()

    JobSearchTool-->>Executor: Jobs

    Executor-->>Agent: Result

    Agent->>Evaluator: Evaluate

    Evaluator-->>Agent: Success

    Agent-->>User: Jobs Found
```

---

## Current Project Structure

```text
catalyst-java/

├── pom.xml
├── Catalyst_Phase1_Agent_Architecture.md
├── Catalyst_Phase2_Claude_Tool_Loop.md
├── go-to/
│   └── Catalyst_Prep_Guide.html
│
└── src/
    ├── main/
    │   └── java/
    │       └── com/
    │           └── catalyst/
    │               │
    │               ├── Application.java
    │               │
    │               ├── core/
    │               │   ├── Agent.java
    │               │   ├── AgentState.java
    │               │   ├── Planner.java
    │               │   ├── Executor.java
    │               │   ├── Evaluator.java
    │               │   ├── AgentLoop.java
    │               │   └── AgentLoopV2.java
    │               │
    │               ├── anthropic/
    │               │   ├── StopReason.java
    │               │   ├── ToolCall.java
    │               │   ├── ToolResult.java
    │               │   ├── ClaudeResponse.java
    │               │   └── MockClaudeClient.java
    │               │
    │               ├── domain/
    │               │   └── Job.java
    │               │
    │               ├── phases/
    │               │   ├── Phase1Runner.java
    │               │   └── Phase2Runner.java
    │               │
    │               └── tools/
    │                   ├── BaseTool.java
    │                   ├── ToolDefinition.java
    │                   ├── ToolRegistry.java
    │                   ├── JobSearchTool.java
    │                   ├── ResumeAnalyzerTool.java
    │                   ├── CalculatorTool.java
    │                   └── WebSearchTool.java
    │
    └── test/
        └── java/
            └── com/
                └── catalyst/
                    ├── PlannerTest.java
                    ├── ToolRegistryTest.java
                    ├── AgentLoopTest.java
                    ├── AgentTest.java
                    └── anthropic/
                        ├── StopReasonTest.java
                        ├── ToolCallTest.java
                        ├── ClaudeResponseTest.java
                        ├── MockClaudeClientTest.java
                        ├── AgentLoopV2Test.java
                        └── SequentialToolExecutionTest.java
```

```text
catalyst-python/

├── pyproject.toml
│
├── catalyst/
│
│   ├── core/
│   │   ├── agent.py
│   │   ├── state.py
│   │   ├── planner.py
│   │   ├── executor.py
│   │   └── evaluator.py
│   │
│   ├── domain/
│   │   └── job.py
│   │
│   ├── tools/
│   │   ├── base_tool.py
│   │   ├── registry.py
│   │   └── job_search_tool.py
│   │
│   ├── agents/
│   │
│   ├── workflows/
│   │
│   ├── context/
│   │
│   ├── memory/
│   │
│   └── mcp/
│
└── tests/
```

---

## Learning Objective (1.1 Agentic Loops)

```text
Goal
 ↓
Plan
 ↓
Select Tool
 ↓
Execute Tool
 ↓
Observe Result
 ↓
Evaluate
 ↓
Respond
```

Claude-style loop (Phase 2+):

```text
Send history -> inspect stop_reason
  tool_use -> run tool, append tool_result, repeat
  end_turn -> return final response
```

Everything later in the Claude Code Certification builds on:

```text
Agent
AgentState
Planner
Executor
Evaluator
AgentLoop / AgentLoopV2
ToolRegistry
StopReason
```
