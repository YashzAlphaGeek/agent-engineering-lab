"""Element 2, the instructions (system prompt).

Immutable and composable: each ``with_...`` method returns a new value, so a shared base can be reused and
specialised per agent. Instructions steer the model. They are advisory, so rules that must never be broken belong
in a gate, not here.
"""

from __future__ import annotations

from dataclasses import dataclass, replace

__all__ = ["Instructions"]


@dataclass(frozen=True)
class Instructions:
    role: str
    rules: tuple[str, ...] = ()
    output_format: str = ""
    examples: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        if self.role is None:
            raise ValueError("role is required")
        object.__setattr__(self, "rules", tuple(self.rules))
        object.__setattr__(self, "examples", tuple(self.examples))
        object.__setattr__(self, "output_format", self.output_format or "")

    @staticmethod
    def for_role(role: str) -> "Instructions":
        return Instructions(role)

    def with_rule(self, rule: str) -> "Instructions":
        return replace(self, rules=self.rules + (rule,))

    def with_output_format(self, output_format: str) -> "Instructions":
        return replace(self, output_format=output_format)

    def with_example(self, example: str) -> "Instructions":
        return replace(self, examples=self.examples + (example,))

    def and_(self, other: "Instructions") -> "Instructions":
        """Merge another set of instructions in, for example a shared safety preamble."""
        return Instructions(
            role=f"{self.role} {other.role}",
            rules=self.rules + other.rules,
            output_format=other.output_format if other.output_format.strip() else self.output_format,
            examples=self.examples + other.examples,
        )

    def render(self) -> str:
        """Render to the system prompt text sent to the model."""
        parts = [self.role]
        if self.rules:
            parts.append("Rules:\n" + "\n".join(f"- {r}" for r in self.rules))
        if self.output_format.strip():
            parts.append("Output format:\n" + self.output_format)
        if self.examples:
            parts.append("Examples:\n" + "\n".join(f"- {e}" for e in self.examples))
        return "\n\n".join(parts)
