"""Element 5, the state of one run.

Holds the ordered message history (the agent's memory, sent on every model call), the goal, the iteration count
and the outcome. A new instance is created for every run, and instances are never shared between agents or runs,
which keeps each agent's context isolated.
"""

from __future__ import annotations

import enum
import uuid

from .model import Message, StopReason

__all__ = ["AgentState", "RunStatus"]


class RunStatus(enum.Enum):
    """Where a run stands. Only ``COMPLETED`` means the model finished normally."""

    RUNNING = "running"
    COMPLETED = "completed"
    LIMIT_REACHED = "limit_reached"
    NOT_FINISHED = "not_finished"
    FAILED = "failed"


class AgentState:
    def __init__(self, goal: str) -> None:
        self._run_id = str(uuid.uuid4())
        self._goal = goal
        self._history: list[Message] = []
        self._succeeded_tools: set[str] = set()
        self._iterations = 0
        self._status = RunStatus.RUNNING
        self._last_stop_reason: StopReason | None = None
        self._final_text = ""
        self._note = ""

    @classmethod
    def start(cls, goal: str) -> "AgentState":
        """Start a run. The goal becomes the first user message."""
        state = cls(goal)
        state._history.append(Message.user(goal))
        return state

    @property
    def run_id(self) -> str:
        return self._run_id

    @property
    def goal(self) -> str:
        return self._goal

    @property
    def iterations(self) -> int:
        return self._iterations

    @property
    def status(self) -> RunStatus:
        return self._status

    @property
    def last_stop_reason(self) -> StopReason | None:
        return self._last_stop_reason

    @property
    def final_text(self) -> str:
        return self._final_text

    @property
    def note(self) -> str:
        return self._note

    @property
    def history(self) -> tuple[Message, ...]:
        return tuple(self._history)

    def append(self, message: Message) -> None:
        self._history.append(message)

    def increment_iteration(self) -> None:
        self._iterations += 1

    def record_stop_reason(self, reason: StopReason) -> None:
        self._last_stop_reason = reason

    def mark_tool_succeeded(self, tool_name: str) -> None:
        """Remember that a tool ran successfully. Gates use this to enforce prerequisites."""
        self._succeeded_tools.add(tool_name)

    def has_succeeded(self, tool_name: str) -> bool:
        return tool_name in self._succeeded_tools

    def complete(self, text: str) -> None:
        self._status = RunStatus.COMPLETED
        self._final_text = text

    def stop(self, reason: RunStatus, explanation: str) -> None:
        if reason in (RunStatus.RUNNING, RunStatus.COMPLETED):
            raise ValueError("Use complete() for a normal finish")
        self._status = reason
        self._note = explanation
