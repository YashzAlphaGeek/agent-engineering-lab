"""Two runnable examples that need no network and no API key.

A scripted model plays the role of the LLM, so the output is the same every time. Replace ``ScriptedModelClient``
with ``AnthropicModelClient`` (or your own model client) to run against a real model.

Run from the project folder:  python examples/demo.py
"""

from __future__ import annotations

import sys
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    import golden_agent  # noqa: F401
except ImportError:  # running from a checkout without installing
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "src"))

from golden_agent import (
    Agent,
    AgentResult,
    FunctionTool,
    Gates,
    InputSchema,
    Instructions,
    ScriptedModelClient,
    Text,
    ToolDefinition,
    ToolResult,
    ToolUse,
    TypedTool,
    end_turn,
    tool_use,
)


# Example 1: one agent, typed tools, a prerequisite gate and a human approval gate


@dataclass(frozen=True)
class RefundRequest:
    order_id: str
    amount_cents: int


class GetCustomerTool(TypedTool[str, str]):
    def __init__(self) -> None:
        super().__init__(
            ToolDefinition(
                "get_customer",
                "Look up and verify a customer by id. Use before any account change. "
                "Returns the verification result.",
                InputSchema.object().string("customerId", "The customer id, for example C-1").build(),
            )
        )

    def decode(self, input: Mapping[str, Any]) -> str:
        return self.string(input, "customerId")

    def run(self, customer_id: str) -> str:
        return f"customer {customer_id} is verified"


class RefundTool(TypedTool[RefundRequest, str]):
    def __init__(self) -> None:
        super().__init__(
            ToolDefinition(
                "process_refund",
                "Refund an order. Requires a verified customer. Returns a confirmation.",
                InputSchema.object()
                .string("orderId", "The order id")
                .integer("amountCents", "Refund amount in cents")
                .build(),
            )
        )

    def decode(self, input: Mapping[str, Any]) -> RefundRequest:
        return RefundRequest(self.string(input, "orderId"), self.integer(input, "amountCents"))

    def run(self, request: RefundRequest) -> str:
        return f"refunded {request.amount_cents} cents for order {request.order_id}"


def single_agent_with_gates() -> None:
    print("=== Example 1: one agent with gates ===")

    model = ScriptedModelClient(
        # The model tries to refund before verifying the customer. The gate blocks it.
        tool_use("t1", "process_refund", {"orderId": "O-1", "amountCents": 9000}),
        tool_use("t2", "get_customer", {"customerId": "C-1"}),
        # Now allowed by the prerequisite gate, but the approval gate asks a human.
        tool_use("t3", "process_refund", {"orderId": "O-1", "amountCents": 9000}),
        end_turn("The refund of 9000 cents for order O-1 has been processed."),
    )

    def approve(call: ToolUse, reason: str) -> bool:
        print(f"  [human] approving {call.name} because: {reason}")
        return True

    support = (
        Agent.builder("support")
        .description("Handles customer refund requests")
        .model(model)
        .instructions(
            Instructions.for_role("You are a customer support agent.")
            .with_rule("Verify the customer before changing an account.")
            .with_output_format("One short confirmation sentence.")
        )
        .tools(GetCustomerTool(), RefundTool())
        .gate(Gates.prerequisites({"process_refund": {"get_customer"}}))
        .gate(Gates.require_approval({"process_refund"}, "refunds need human approval"))
        .approver(approve)
        .max_iterations(10)
        .build()
    )

    show(support.run("Refund order O-1 for customer C-1, 9000 cents"))


# Example 2: a coordinator that delegates to two sub agents, each with its own tools


def coordinator_with_sub_agents() -> None:
    print("=== Example 2: coordinator with sub agents ===")

    researcher = Agent(
        "researcher",
        description="Finds facts on a topic and returns claims with their sources",
        model=ScriptedModelClient(
            tool_use("r1", "web_search", {"query": "java jobs munich"}),
            end_turn("Claim: 12 Java roles in Munich. Source: example-jobs.test/munich"),
        ),
        instructions=Instructions.for_role("You are a research agent.").with_rule(
            "Always return each claim with its source."
        ),
        tools=[
            FunctionTool.of(
                ToolDefinition(
                    "web_search",
                    "Search the web. Returns a short list of results.",
                    InputSchema.object().string("query", "Search query").build(),
                ),
                lambda args: f"12 results for {args['query']}",
            )
        ],
    )

    analyst = Agent(
        "analyst",
        description="Analyses findings and returns strengths and gaps",
        model=ScriptedModelClient(end_turn("Gap: Spring Boot experience. Strength: Java fundamentals.")),
        instructions=Instructions.for_role("You are an analysis agent."),
    )

    coordinator = Agent(
        "coordinator",
        description="Plans, delegates and aggregates",
        model=ScriptedModelClient(
            tool_use(
                "c1",
                "delegate_to_researcher",
                {"task": "Find Java jobs in Munich", "context": "Goal: job search"},
            ),
            tool_use(
                "c2",
                "delegate_to_analyst",
                {
                    "task": "Analyse the findings",
                    "context": "Finding: 12 Java roles in Munich (example-jobs.test/munich)",
                },
            ),
            end_turn("Report: 12 roles found. Gap: Spring Boot. Sources: example-jobs.test/munich"),
        ),
        instructions=Instructions.for_role("You are a coordinator.").with_rule(
            "Pass each sub agent all the context it needs."
        ),
        sub_agents=[researcher, analyst],
    )

    show(coordinator.run("Find Java jobs in Munich and analyse my skills"))


# Printing


def show(result: AgentResult) -> None:
    for message in result.history:
        for block in message.content:
            match block:
                case Text(text=text):
                    print(f"  {message.role.name} says: {text}")
                case ToolUse(name=name, input=args):
                    print(f"  model asks for {name} {dict(args)}")
                case ToolResult(content=content, is_error=is_error):
                    print(f"  tool_result{' (error)' if is_error else ''}: {content}")
    stop = result.last_stop_reason.name if result.last_stop_reason else None
    print(f"  -> status={result.status.name} iterations={result.iterations} lastStopReason={stop}")


if __name__ == "__main__":
    single_agent_with_gates()
    print()
    coordinator_with_sub_agents()
