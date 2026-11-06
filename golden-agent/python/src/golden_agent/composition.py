"""The public entry point: one agent made of the five core elements.

    agent = (
        Agent.builder("support")
        .model(model_client)                                       # element 1
        .instructions(Instructions.for_role("You are support."))   # element 2
        .tool(lookup_tool)                                         # element 3
        .max_iterations(10)                                        # element 4, loop limits
        .gate(Gates.prerequisites(rules))                          # guardrail
        .build()
    )
    result = agent.run("Refund order 123")                         # element 5, a fresh state per run

An ``Agent`` is immutable and holds no run state, so one instance can be reused and run many times, even
concurrently. Every ``run`` creates its own isolated state.
"""

from __future__ import annotations

import re
from collections.abc import Callable, Iterable, Mapping
from typing import Any

from .guard import (
    Gate,
    HumanApprover,
    ResultFilter,
    all_of,
    allow_all,
    deny_all,
    identity_filter,
)
from .instructions import Instructions
from .loop import AgentLoop, AgentResult, LoopConfig
from .model import ModelClient, ModelRequest, ModelResponse, as_model_client
from .tool import InputSchema, Tool, ToolDefinition, ToolError, ToolOutput, ToolRegistry

__all__ = ["Agent", "AgentBuilder", "DelegationTool"]

_VALID_NAME = re.compile(r"[a-zA-Z0-9_-]{1,50}")


class Agent:
    """One agent. Construct it with keyword arguments or with ``Agent.builder(name)``."""

    def __init__(
        self,
        name: str,
        *,
        model: "ModelClient | Callable[[ModelRequest], ModelResponse]",
        instructions: Instructions,
        description: str | None = None,
        tools: "Iterable[Tool] | ToolRegistry" = (),
        sub_agents: "Iterable[Agent]" = (),
        gate: "Gate | Iterable[Gate] | None" = None,
        approver: HumanApprover | None = None,
        result_filter: ResultFilter | None = None,
        config: LoopConfig | None = None,
    ) -> None:
        if not isinstance(name, str) or not _VALID_NAME.fullmatch(name):
            raise ValueError(f"Agent name must match [a-zA-Z0-9_-]{{1,50}}: {name!r}")
        if model is None:
            raise ValueError("model is required")
        if instructions is None:
            raise ValueError("instructions are required")

        registry = ToolRegistry()
        tool_iterable = tools.all() if isinstance(tools, ToolRegistry) else tools
        for tool in tool_iterable:
            registry.register(tool)
        for sub in sub_agents:
            registry.register(sub.as_tool())

        if gate is None:
            combined = allow_all()
        elif callable(gate):
            combined = gate
        else:
            combined = all_of(*gate)

        self._name = name
        self._description = description or f"Delegates a task to the {name} agent"
        self._model = as_model_client(model)
        self._instructions = instructions
        self._tools = registry
        self._gate = combined
        self._approver = approver or deny_all()
        self._filter = result_filter or identity_filter()
        self._config = config or LoopConfig()

    @staticmethod
    def builder(name: str) -> "AgentBuilder":
        return AgentBuilder(name)

    @property
    def name(self) -> str:
        return self._name

    @property
    def description(self) -> str:
        return self._description

    @property
    def tools(self) -> ToolRegistry:
        return self._tools

    def run(self, goal: str) -> AgentResult:
        """Run the agent on a goal. Safe to call repeatedly and concurrently."""
        loop = AgentLoop(
            self._model, self._instructions, self._tools, self._gate, self._approver, self._filter, self._config
        )
        return loop.run(goal)

    def as_tool(self) -> Tool:
        """Expose this agent to another agent as a delegation tool (the sub agent pattern)."""
        return DelegationTool(self)


class AgentBuilder:
    def __init__(self, name: str) -> None:
        if not isinstance(name, str) or not _VALID_NAME.fullmatch(name):
            raise ValueError(f"Agent name must match [a-zA-Z0-9_-]{{1,50}}: {name!r}")
        self._name = name
        self._description: str | None = None
        self._model: Any = None
        self._instructions: Instructions | None = None
        self._tools: list[Tool] = []
        self._sub_agents: list[Agent] = []
        self._gates: list[Gate] = []
        self._approver: HumanApprover | None = None
        self._filter: ResultFilter | None = None
        self._config = LoopConfig()

    def description(self, description: str) -> "AgentBuilder":
        """What this agent is for. When used as a sub agent this is what the coordinator reads."""
        self._description = description
        return self

    def model(self, model: Any) -> "AgentBuilder":
        self._model = model
        return self

    def instructions(self, instructions: Instructions) -> "AgentBuilder":
        self._instructions = instructions
        return self

    def tool(self, tool: Tool) -> "AgentBuilder":
        self._tools.append(tool)
        return self

    def tools(self, *more: "Tool | ToolRegistry") -> "AgentBuilder":
        for item in more:
            if isinstance(item, ToolRegistry):
                self._tools.extend(item.all())
            else:
                self._tools.append(item)
        return self

    def sub_agent(self, sub: Agent) -> "AgentBuilder":
        """Give this agent a sub agent, exposed as a delegation tool."""
        self._sub_agents.append(sub)
        return self

    def gate(self, gate: Gate) -> "AgentBuilder":
        """Add a gate. Several gates combine, and the first non-Allow decision wins."""
        self._gates.append(gate)
        return self

    def approver(self, approver: HumanApprover) -> "AgentBuilder":
        self._approver = approver
        return self

    def result_filter(self, result_filter: ResultFilter) -> "AgentBuilder":
        self._filter = result_filter
        return self

    def config(self, config: LoopConfig) -> "AgentBuilder":
        self._config = config
        return self

    def max_iterations(self, maximum: int) -> "AgentBuilder":
        self._config = self._config.with_max_iterations(maximum)
        return self

    def build(self) -> Agent:
        if self._model is None:
            raise ValueError("model is required")
        if self._instructions is None:
            raise ValueError("instructions are required")
        return Agent(
            self._name,
            model=self._model,
            instructions=self._instructions,
            description=self._description,
            tools=self._tools,
            sub_agents=self._sub_agents,
            gate=self._gates or None,
            approver=self._approver,
            result_filter=self._filter,
            config=self._config,
        )


class DelegationTool:
    """A tool whose implementation is another agent. This is how a coordinator delegates to a sub agent.

    The sub agent never sees the coordinator's conversation. It receives only the ``task`` and the explicit
    ``context`` that the coordinator passes. When the sub agent does not finish, the tool returns a structured
    error (what was attempted, status, partial text) instead of an empty success, so the coordinator can retry,
    route elsewhere, or continue with partial results.
    """

    def __init__(self, agent: Agent) -> None:
        self._agent = agent
        self._definition = ToolDefinition(
            name=f"delegate_to_{agent.name}",
            description=agent.description,
            input_schema=InputSchema.object()
            .string("task", "The sub-task to perform, stated completely", True)
            .string("context", "Everything the sub agent needs, such as goal and prior findings", False)
            .build(),
        )

    def definition(self) -> ToolDefinition:
        return self._definition

    def execute(self, input: Mapping[str, Any]) -> ToolOutput:
        task = input.get("task")
        if not isinstance(task, str) or not task.strip():
            raise ToolError("Missing required argument 'task'")
        context = input.get("context")
        context_text = context if isinstance(context, str) else ""
        goal = task if not context_text.strip() else f"{task}\n\nContext:\n{context_text}"

        result = self._agent.run(goal)
        if result.completed:
            return ToolOutput.ok(result.text)
        return ToolOutput.error(
            f'SUB_AGENT_FAILURE agent={self._agent.name} status={result.status.name} '
            f'iterations={result.iterations} note="{result.note}" task="{task}" partial="{result.text}"'
        )
