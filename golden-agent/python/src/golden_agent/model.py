"""Element 1, the model.

This module holds the message types and the ``ModelClient`` protocol, which is the only place an LLM is reached.
Implement it once per provider (Anthropic API, Bedrock, Vertex, a test double). The loop depends on this
protocol only, so providers are interchangeable.
"""

from __future__ import annotations

import enum
from collections.abc import Callable, Iterable, Mapping
from dataclasses import dataclass, field
from types import MappingProxyType
from typing import Any, Protocol, Union, runtime_checkable

from .tool import ToolDefinition

__all__ = [
    "ContentBlock",
    "Message",
    "ModelClient",
    "ModelError",
    "ModelRequest",
    "ModelResponse",
    "Role",
    "ScriptedModelClient",
    "StopReason",
    "Text",
    "ToolResult",
    "ToolUse",
    "as_model_client",
    "end_turn",
    "stopped_with",
    "tool_use",
]


class ModelError(Exception):
    """A model call failed (network, rate limit, overload). The loop turns it into a FAILED run."""


class Role(enum.Enum):
    """Who produced a message in the conversation history."""

    USER = "user"
    ASSISTANT = "assistant"


class StopReason(enum.Enum):
    """Why the model stopped. This is the only reliable signal for controlling the loop.

    Anything other than ``END_TURN`` or ``TOOL_USE`` means "not finished, find out why".
    """

    END_TURN = "end_turn"
    TOOL_USE = "tool_use"
    MAX_TOKENS = "max_tokens"
    STOP_SEQUENCE = "stop_sequence"
    PAUSE_TURN = "pause_turn"
    REFUSAL = "refusal"
    CONTEXT_WINDOW_EXCEEDED = "model_context_window_exceeded"

    @classmethod
    def from_api(cls, value: object) -> "StopReason":
        """Map a provider string to the enum. An unknown value is rejected, never treated as a normal finish."""
        for member in cls:
            if member.value == value:
                return member
        raise ModelError(f"Unknown stop_reason {value!r}")


@dataclass(frozen=True)
class Text:
    """Plain text from the user or the model."""

    text: str


@dataclass(frozen=True)
class ToolUse:
    """The model asks the application to run a tool. It is a request, never an execution."""

    id: str
    name: str
    input: Mapping[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        object.__setattr__(self, "input", MappingProxyType(dict(self.input or {})))


@dataclass(frozen=True)
class ToolResult:
    """The application's answer to a ``ToolUse``, matched by ``tool_use_id``."""

    tool_use_id: str
    content: str
    is_error: bool = False


ContentBlock = Union[Text, ToolUse, ToolResult]


@dataclass(frozen=True)
class Message:
    """One turn in the conversation. The full list of messages is the agent's memory."""

    role: Role
    content: tuple[ContentBlock, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "content", tuple(self.content))

    @staticmethod
    def user(text: str) -> "Message":
        return Message(Role.USER, (Text(text),))

    @staticmethod
    def assistant(blocks: Iterable[ContentBlock]) -> "Message":
        return Message(Role.ASSISTANT, tuple(blocks))

    @staticmethod
    def tool_results(results: Iterable[ToolResult]) -> "Message":
        """Tool results travel back to the model inside a user message."""
        return Message(Role.USER, tuple(results))


@dataclass(frozen=True)
class ModelRequest:
    """Everything sent to the model on one call. The API is stateless, so history is always included."""

    system: str
    messages: tuple[Message, ...]
    tools: tuple[ToolDefinition, ...]
    max_tokens: int

    def __post_init__(self) -> None:
        object.__setattr__(self, "system", self.system or "")
        object.__setattr__(self, "messages", tuple(self.messages))
        object.__setattr__(self, "tools", tuple(self.tools))
        if self.max_tokens <= 0:
            raise ValueError("max_tokens must be positive")


@dataclass(frozen=True)
class ModelResponse:
    """What comes back from the model: content blocks plus the reason it stopped."""

    content: tuple[ContentBlock, ...]
    stop_reason: StopReason

    def __post_init__(self) -> None:
        object.__setattr__(self, "content", tuple(self.content))
        if not isinstance(self.stop_reason, StopReason):
            raise TypeError("stop_reason must be a StopReason")

    @property
    def text(self) -> str:
        """All text blocks joined together."""
        return "\n".join(b.text for b in self.content if isinstance(b, Text))

    @property
    def tool_uses(self) -> tuple[ToolUse, ...]:
        """All tool requests in this response. A response may contain several."""
        return tuple(b for b in self.content if isinstance(b, ToolUse))


@runtime_checkable
class ModelClient(Protocol):
    """Anything with ``send(request) -> response``. Raise ``ModelError`` when the call fails."""

    def send(self, request: ModelRequest) -> ModelResponse: ...


class _FunctionModelClient:
    def __init__(self, fn: Callable[[ModelRequest], ModelResponse]) -> None:
        self._fn = fn

    def send(self, request: ModelRequest) -> ModelResponse:
        return self._fn(request)


def as_model_client(candidate: "ModelClient | Callable[[ModelRequest], ModelResponse]") -> ModelClient:
    """Accept either a ``ModelClient`` or a plain function, so a lambda works as a model."""
    if hasattr(candidate, "send"):
        return candidate  # type: ignore[return-value]
    if callable(candidate):
        return _FunctionModelClient(candidate)
    raise TypeError("model must have a send() method or be callable")


# Response building helpers


def end_turn(text: str) -> ModelResponse:
    return ModelResponse((Text(text),), StopReason.END_TURN)


def tool_use(id: str, tool_name: str, input: Mapping[str, Any] | None = None) -> ModelResponse:
    return ModelResponse((ToolUse(id, tool_name, input or {}),), StopReason.TOOL_USE)


def stopped_with(reason: StopReason) -> ModelResponse:
    return ModelResponse((Text("(stopped)"),), reason)


class ScriptedModelClient:
    """A deterministic test double for ``ModelClient``. It replays a fixed script of responses.

    Running past the end of the script raises, so a loop that asks the model more often than expected is caught
    instead of being hidden.
    """

    def __init__(self, *responses: ModelResponse) -> None:
        self._script = list(responses)
        self._requests: list[ModelRequest] = []

    def send(self, request: ModelRequest) -> ModelResponse:
        self._requests.append(request)
        if not self._script:
            raise RuntimeError(f"Script exhausted after {len(self._requests)} calls")
        return self._script.pop(0)

    @property
    def requests(self) -> tuple[ModelRequest, ...]:
        """Every request received so far, for assertions."""
        return tuple(self._requests)

    @property
    def calls(self) -> int:
        return len(self._requests)

    # The same helpers, reachable from the class for readability in scripts
    end_turn = staticmethod(end_turn)
    tool_use = staticmethod(tool_use)
    stopped_with = staticmethod(stopped_with)
