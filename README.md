# Catalyst Architecture

## Setup

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -e .
pip list
```

---

## Agent Architecture

```mermaid
flowchart TD

    U[User Goal]

    U --> A[Agent]

    A --> P[Planner]

    P --> S[AgentState]

    P --> T[Tool Registry]

    T --> JT[JobSearchTool]

    P --> E[Executor]

    E --> JT

    JT --> R[Result]

    R --> EV[Evaluator]

    EV --> S

    EV --> C{Success?}

    C -->|Yes| F[Final Response]

    C -->|No| P
```

---

## Core Components

```mermaid
classDiagram

    class Agent {
        +run(goal)
    }

    class AgentState {
        +goal
        +plan
        +result
        +completed
        +history
    }

    class Planner {
        +createPlan(goal)
    }

    class Executor {
        +execute(tool, request)
    }

    class Evaluator {
        +evaluate(result)
    }

    Agent --> Planner
    Agent --> Executor
    Agent --> Evaluator
    Agent --> AgentState
```

---

## Request Flow

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
│
└── src/
    └── main/
        └── java/
            └── com/
                └── catalyst/
                    │
                    ├── Application.java
                    │
                    ├── core/
                    │   ├── Agent.java
                    │   ├── AgentState.java
                    │   ├── Planner.java
                    │   ├── Executor.java
                    │   └── Evaluator.java
                    │
                    ├── domain/
                    │   └── Job.java
                    │
                    └── tools/
                        ├── BaseTool.java
                        ├── ToolRegistry.java
                        └── JobSearchTool.java
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

Everything later in the Claude Code Certification builds on:

```text
Agent
AgentState
Planner
Executor
Evaluator
```
