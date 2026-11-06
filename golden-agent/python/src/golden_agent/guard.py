"""The guard layer: programmatic checks around every tool call.

A gate is ordinary code that runs before a tool call, outside the model, so the model cannot skip it or argue
with it. A gate returns one of three decisions: allow, deny, or ask a human.
"""

from __future__ import annotations

from collections.abc import Callable, Collection, Mapping
from dataclasses import dataclass
from typing import Union

from .model import ToolUse
from .state import AgentState
from .tool import ToolOutput

__all__ = [
    "Allow",
    "AskHuman",
    "Decision",
    "Deny",
    "Gate",
    "Gates",
    "HumanApprover",
    "ResultFilter",
    "all_of",
    "allow",
    "allow_all",
    "ask_human",
    "deny",
    "deny_all",
    "identity_filter",
]


@dataclass(frozen=True)
class Allow:
    """The call may run."""


@dataclass(frozen=True)
class Deny:
    """The call must not run. The reason is returned to the model as an error result."""

    reason: str


@dataclass(frozen=True)
class AskHuman:
    """A person must approve first. No answer means deny."""

    reason: str


Decision = Union[Allow, Deny, AskHuman]

# A gate is any callable taking the requested tool call and the run state, and returning a decision.
Gate = Callable[[ToolUse, AgentState], Decision]

# Asked when a gate returns AskHuman. Plug in a console prompt, a ticket, a chat message or a web form.
HumanApprover = Callable[[ToolUse, str], bool]

# Runs after a tool and before the model sees the result. Use it to trim, normalise or redact.
ResultFilter = Callable[[ToolUse, ToolOutput], ToolOutput]


def allow() -> Decision:
    return Allow()


def deny(reason: str) -> Decision:
    return Deny(reason)


def ask_human(reason: str) -> Decision:
    return AskHuman(reason)


def allow_all() -> Gate:
    return lambda call, state: Allow()


def all_of(*gates: Gate) -> Gate:
    """Combine gates. The first decision that is not Allow wins."""

    def combined(call: ToolUse, state: AgentState) -> Decision:
        for gate in gates:
            decision = gate(call, state)
            if not isinstance(decision, Allow):
                return decision
        return Allow()

    return combined


def deny_all() -> HumanApprover:
    """The default approver. It fails closed: nothing is approved unless a person says so."""
    return lambda call, reason: False


def identity_filter() -> ResultFilter:
    return lambda call, output: output


class Gates:
    """Ready-made gates for the most common rules."""

    @staticmethod
    def prerequisites(requires: Mapping[str, Collection[str]]) -> Gate:
        """Block a tool until other tools have succeeded first.

        Example: ``{"process_refund": {"get_customer"}}``.
        """

        def gate(call: ToolUse, state: AgentState) -> Decision:
            needed = requires.get(call.name, ())
            missing = sorted(t for t in needed if not state.has_succeeded(t))
            if not missing:
                return Allow()
            return Deny(f"'{call.name}' requires these tools to succeed first: {missing}")

        return gate

    @staticmethod
    def require_approval(tool_names: Collection[str], reason: str) -> Gate:
        """Ask a human before any of the named tools run."""
        names = frozenset(tool_names)
        return lambda call, state: AskHuman(reason) if call.name in names else Allow()

    @staticmethod
    def allow_list(tool_names: Collection[str]) -> Gate:
        """Only the named tools may run, whatever the registry holds."""
        names = frozenset(tool_names)
        return lambda call, state: (
            Allow() if call.name in names else Deny(f"Tool '{call.name}' is not permitted for this agent")
        )
