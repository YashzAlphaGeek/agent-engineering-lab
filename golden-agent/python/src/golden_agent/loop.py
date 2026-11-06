"""Element 4, the loop. The application code around the model.

Each pass: send the full history and tools, read ``stop_reason``, and either finish (``END_TURN``), run the
requested tools and loop (``TOOL_USE``), or stop because the model ended for another reason. Every tool call
passes the gate first, and failures become error results the model can read.

The loop depends only on abstractions (the model client, the tool registry, the gate), so any provider or tool
set can be plugged in. It is stateless between runs.
"""

from __future__ import annotations

from dataclasses import dataclass

from .guard import Allow, AskHuman, Deny, Gate, HumanApprover, ResultFilter
from .instructions import Instructions
from .model import (
    Message,
    ModelClient,
    ModelError,
    ModelRequest,
    ModelResponse,
    StopReason,
    ToolResult,
    ToolUse,
)
from .state import AgentState, RunStatus
from .tool import ToolError, ToolOutput, ToolRegistry

__all__ = ["AgentLoop", "AgentResult", "LoopConfig"]


@dataclass(frozen=True)
class LoopConfig:
    """Limits for one run. The iteration cap is a guard rail, never the normal way to stop."""

    max_iterations: int = 20
    max_tokens: int = 1024

    def __post_init__(self) -> None:
        if self.max_iterations <= 0 or self.max_tokens <= 0:
            raise ValueError("max_iterations and max_tokens must be positive")

    def with_max_iterations(self, value: int) -> "LoopConfig":
        return LoopConfig(value, self.max_tokens)

    def with_max_tokens(self, value: int) -> "LoopConfig":
        return LoopConfig(self.max_iterations, value)


@dataclass(frozen=True)
class AgentResult:
    """The outcome of one run, copied out of the state so callers cannot change it."""

    status: RunStatus
    text: str
    note: str
    iterations: int
    last_stop_reason: StopReason | None
    history: tuple[Message, ...]

    @property
    def completed(self) -> bool:
        return self.status is RunStatus.COMPLETED

    @staticmethod
    def from_state(state: AgentState) -> "AgentResult":
        return AgentResult(
            status=state.status,
            text=state.final_text,
            note=state.note,
            iterations=state.iterations,
            last_stop_reason=state.last_stop_reason,
            history=state.history,
        )


class AgentLoop:
    def __init__(
        self,
        model: ModelClient,
        instructions: Instructions,
        tools: ToolRegistry,
        gate: Gate,
        approver: HumanApprover,
        result_filter: ResultFilter,
        config: LoopConfig,
    ) -> None:
        self._model = model
        self._instructions = instructions
        self._tools = tools
        self._gate = gate
        self._approver = approver
        self._filter = result_filter
        self._config = config

    def run(self, goal: str) -> AgentResult:
        """Run to completion and return the outcome. Each call uses a fresh, isolated state."""
        state = AgentState.start(goal)
        system = self._instructions.render()

        while state.status is RunStatus.RUNNING:
            if state.iterations >= self._config.max_iterations:
                state.stop(RunStatus.LIMIT_REACHED, f"Iteration cap of {self._config.max_iterations} reached")
                break
            state.increment_iteration()

            try:
                response = self._model.send(
                    ModelRequest(system, state.history, self._tools.definitions(), self._config.max_tokens)
                )
            except ModelError as error:
                state.stop(RunStatus.FAILED, f"Model call failed: {error}")
                break

            state.record_stop_reason(response.stop_reason)
            state.append(Message.assistant(response.content))

            match response.stop_reason:
                case StopReason.END_TURN:
                    state.complete(response.text)
                case StopReason.TOOL_USE:
                    self._handle_tool_use(response, state)
                case other:
                    state.stop(
                        RunStatus.NOT_FINISHED,
                        f"Model stopped with {other.name}, which is not a normal finish",
                    )
        return AgentResult.from_state(state)

    def _handle_tool_use(self, response: ModelResponse, state: AgentState) -> None:
        requests = response.tool_uses
        if not requests:
            state.stop(RunStatus.FAILED, "stop_reason was tool_use but the response had no tool_use block")
            return
        results = [self._run_one(request, state) for request in requests]
        # All results for one assistant turn go back together in a single user message.
        state.append(Message.tool_results(results))

    def _run_one(self, request: ToolUse, state: AgentState) -> ToolResult:
        decision = self._gate(request, state)

        match decision:
            case Allow():
                output = self._execute(request, state)
            case Deny(reason=reason):
                output = ToolOutput.error(f"Blocked: {reason}")
            case AskHuman(reason=reason):
                if self._approver(request, reason):
                    output = self._execute(request, state)
                else:
                    output = ToolOutput.error(f"Not approved by a human: {reason}")
            case _:
                output = ToolOutput.error("Blocked: the gate returned an unrecognised decision")

        visible = self._filter(request, output)
        return ToolResult(request.id, visible.content, visible.is_error)

    def _execute(self, request: ToolUse, state: AgentState) -> ToolOutput:
        tool = self._tools.find(request.name)
        if tool is None:
            available = sorted(self._tools.names())
            return ToolOutput.error(f"Unknown tool '{request.name}'. Available: {available}")
        try:
            output = tool.execute(request.input)
        except ToolError as error:
            return ToolOutput.error(str(error))
        except Exception as error:  # noqa: BLE001 - tool code is untrusted, never leak its internals
            return ToolOutput.error(f"Tool '{request.name}' failed unexpectedly: {type(error).__name__}")
        if not output.is_error:
            state.mark_tool_succeeded(request.name)
        return output
