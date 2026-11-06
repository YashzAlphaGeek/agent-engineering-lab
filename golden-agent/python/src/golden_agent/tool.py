"""Element 3, tools.

A tool is a described capability the model may ask for: a name, a description and an input schema that the
model sees, plus code that runs it. Behind a tool can be an API call, a database query, a local function,
an MCP server, or another agent (see ``DelegationTool``).

This module has no dependency on the rest of the library.
"""

from __future__ import annotations

import re
from abc import ABC, abstractmethod
from collections.abc import Callable, Collection, Iterable, Mapping
from dataclasses import dataclass, field
from types import MappingProxyType
from typing import Any, Generic, Protocol, TypeVar, runtime_checkable

__all__ = [
    "FunctionTool",
    "InputSchema",
    "Property",
    "Tool",
    "ToolDefinition",
    "ToolError",
    "ToolOutput",
    "ToolRegistry",
    "TypedTool",
]

_VALID_NAME = re.compile(r"[a-zA-Z0-9_-]{1,64}")


class ToolError(Exception):
    """An expected tool failure, such as bad arguments or a missing record. Its message goes back to the model."""


@dataclass(frozen=True)
class Property:
    type: str
    description: str


@dataclass(frozen=True)
class InputSchema:
    """A small JSON Schema for a tool's input, built without any library."""

    properties: Mapping[str, Property] = field(default_factory=dict)
    required: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        object.__setattr__(self, "properties", MappingProxyType(dict(self.properties)))
        object.__setattr__(self, "required", tuple(self.required))

    @staticmethod
    def object() -> "InputSchemaBuilder":
        return InputSchemaBuilder()

    def to_json_schema(self) -> dict[str, Any]:
        """The JSON Schema form that providers expect as ``input_schema``."""
        return {
            "type": "object",
            "properties": {
                name: {"type": p.type, "description": p.description} for name, p in self.properties.items()
            },
            "required": list(self.required),
        }


class InputSchemaBuilder:
    def __init__(self) -> None:
        self._properties: dict[str, Property] = {}
        self._required: list[str] = []

    def _add(self, name: str, type_: str, description: str, required: bool) -> "InputSchemaBuilder":
        self._properties[name] = Property(type_, description)
        if required and name not in self._required:
            self._required.append(name)
        return self

    def string(self, name: str, description: str, required: bool = True) -> "InputSchemaBuilder":
        return self._add(name, "string", description, required)

    def integer(self, name: str, description: str, required: bool = True) -> "InputSchemaBuilder":
        return self._add(name, "integer", description, required)

    def number(self, name: str, description: str, required: bool = True) -> "InputSchemaBuilder":
        return self._add(name, "number", description, required)

    def boolean(self, name: str, description: str, required: bool = True) -> "InputSchemaBuilder":
        return self._add(name, "boolean", description, required)

    def build(self) -> InputSchema:
        return InputSchema(self._properties, tuple(self._required))


@dataclass(frozen=True)
class ToolDefinition:
    """What the model sees about a tool.

    The description is the only way the model knows when to use the tool, so write it carefully: what it does,
    when to use it, when not to, and what it returns.
    """

    name: str
    description: str
    input_schema: InputSchema = field(default_factory=InputSchema)

    def __post_init__(self) -> None:
        if not isinstance(self.name, str) or not _VALID_NAME.fullmatch(self.name):
            raise ValueError(f"Tool name must match [a-zA-Z0-9_-]{{1,64}}: {self.name!r}")
        if not self.description or not self.description.strip():
            raise ValueError(f"Tool '{self.name}' needs a description")


@dataclass(frozen=True)
class ToolOutput:
    """The result of running a tool. A failure is data the model can read and react to, not a crash."""

    content: str
    is_error: bool = False

    @staticmethod
    def ok(content: str) -> "ToolOutput":
        return ToolOutput(content, False)

    @staticmethod
    def error(message: str) -> "ToolOutput":
        return ToolOutput(message, True)


@runtime_checkable
class Tool(Protocol):
    """A tool is anything with a ``definition()`` and an ``execute(input)``."""

    def definition(self) -> ToolDefinition: ...

    def execute(self, input: Mapping[str, Any]) -> ToolOutput: ...


def _check_required(definition: ToolDefinition, input: Mapping[str, Any]) -> None:
    for key in definition.input_schema.required:
        if input.get(key) is None:
            raise ToolError(f"Missing required argument '{key}'")


I = TypeVar("I")
O = TypeVar("O")


class TypedTool(ABC, Generic[I, O]):
    """A tool with a typed input ``I`` and a typed output ``O``.

    ``execute`` is a template method: it validates required arguments, decodes the raw mapping into ``I``, runs
    the typed logic and encodes ``O`` as text for the model. Subclasses write only ``decode``, ``run`` and
    optionally ``encode``.
    """

    def __init__(self, definition: ToolDefinition) -> None:
        self._definition = definition

    def definition(self) -> ToolDefinition:
        return self._definition

    def execute(self, input: Mapping[str, Any]) -> ToolOutput:
        _check_required(self._definition, input)
        typed = self.decode(input)
        result = self.run(typed)
        return ToolOutput.ok(self.encode(result))

    @abstractmethod
    def decode(self, input: Mapping[str, Any]) -> I:
        """Turn the raw argument mapping into the typed input."""

    @abstractmethod
    def run(self, input: I) -> O:
        """Do the work."""

    def encode(self, output: O) -> str:
        """Turn the typed output into the text returned to the model."""
        return str(output)

    # Decoding helpers

    @staticmethod
    def string(input: Mapping[str, Any], key: str) -> str:
        value = input.get(key)
        if not isinstance(value, str):
            raise ToolError(f"Argument '{key}' must be a string")
        return value

    @staticmethod
    def integer(input: Mapping[str, Any], key: str) -> int:
        value = input.get(key)
        if isinstance(value, bool) or not isinstance(value, (int, float)) or value != int(value):
            raise ToolError(f"Argument '{key}' must be an integer")
        return int(value)


class FunctionTool:
    """The quickest way to make a tool: a definition plus a function that returns text."""

    def __init__(self, definition: ToolDefinition, body: Callable[[Mapping[str, Any]], str]) -> None:
        self._definition = definition
        self._body = body

    @classmethod
    def of(cls, definition: ToolDefinition, body: Callable[[Mapping[str, Any]], str]) -> "FunctionTool":
        return cls(definition, body)

    def definition(self) -> ToolDefinition:
        return self._definition

    def execute(self, input: Mapping[str, Any]) -> ToolOutput:
        _check_required(self._definition, input)
        return ToolOutput.ok(self._body(input))


class ToolRegistry:
    """The set of tools an agent may use.

    Only registered tools can run, which makes the registry a safety boundary. ``subset`` gives a sub agent a
    restricted view (least privilege).
    """

    def __init__(self, tools: Iterable[Tool] = ()) -> None:
        self._tools: dict[str, Tool] = {}
        for tool in tools:
            self.register(tool)

    @classmethod
    def of(cls, *tools: Tool) -> "ToolRegistry":
        return cls(tools)

    def register(self, tool: Tool) -> "ToolRegistry":
        name = tool.definition().name
        if name in self._tools:
            raise ValueError(f"Duplicate tool: {name}")
        self._tools[name] = tool
        return self

    def find(self, name: str) -> Tool | None:
        return self._tools.get(name)

    def definitions(self) -> tuple[ToolDefinition, ...]:
        return tuple(t.definition() for t in self._tools.values())

    def names(self) -> frozenset[str]:
        return frozenset(self._tools)

    def all(self) -> tuple[Tool, ...]:
        """All registered tools in registration order."""
        return tuple(self._tools.values())

    def subset(self, names: Collection[str]) -> "ToolRegistry":
        """A new registry holding only the named tools."""
        restricted = ToolRegistry()
        for name in names:
            tool = self._tools.get(name)
            if tool is None:
                raise ValueError(f"Unknown tool: {name}")
            restricted.register(tool)
        return restricted

    def __len__(self) -> int:
        return len(self._tools)

    def __contains__(self, name: object) -> bool:
        return name in self._tools
