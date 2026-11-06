"""Offline tests for the Anthropic adapter. A fake transport stands in for the network."""

from __future__ import annotations

import json
import unittest
from typing import Any

from golden_agent import (
    AnthropicModelClient,
    Instructions,
    ModelError,
    ModelRequest,
    StopReason,
    Agent,
    RunStatus,
)

OK_BODY = json.dumps({"content": [{"type": "text", "text": "hi"}], "stop_reason": "end_turn"})


def request() -> ModelRequest:
    from golden_agent import Message

    return ModelRequest("sys", (Message.user("hello"),), (), 64)


class FakeTransport:
    """Replays a list of outcomes. An outcome is a (status, body) tuple or an exception to raise."""

    def __init__(self, *outcomes: Any) -> None:
        self.outcomes = list(outcomes)
        self.calls: list[dict[str, Any]] = []

    def __call__(self, url: str, headers: Any, body: bytes, timeout: float) -> tuple[int, str]:
        self.calls.append({"url": url, "headers": dict(headers), "body": json.loads(body), "timeout": timeout})
        outcome = self.outcomes.pop(0)
        if isinstance(outcome, BaseException):
            raise outcome
        return outcome


def client(transport: FakeTransport, sleeps: list[float], **options: Any) -> AnthropicModelClient:
    return AnthropicModelClient("key-123", "some-model", transport=transport, sleep=sleeps.append, **options)


class AnthropicClientTests(unittest.TestCase):
    def test_a_successful_call_sends_the_right_request_and_parses_the_reply(self) -> None:
        transport = FakeTransport((200, OK_BODY))
        response = client(transport, []).send(request())
        sent = transport.calls[0]
        self.assertEqual(response.text, "hi")
        self.assertIs(response.stop_reason, StopReason.END_TURN)
        self.assertEqual(sent["url"], "https://api.anthropic.com/v1/messages")
        self.assertEqual(sent["headers"]["x-api-key"], "key-123")
        self.assertEqual(sent["headers"]["anthropic-version"], "2023-06-01")
        self.assertEqual(sent["body"]["model"], "some-model")

    def test_rate_limits_are_retried_with_exponential_backoff(self) -> None:
        sleeps: list[float] = []
        transport = FakeTransport((429, "slow down"), (503, "busy"), (200, OK_BODY))
        response = client(transport, sleeps).send(request())
        self.assertEqual(response.text, "hi")
        self.assertEqual(len(transport.calls), 3)
        self.assertEqual(sleeps, [0.5, 1.0])

    def test_network_errors_are_retried(self) -> None:
        sleeps: list[float] = []
        transport = FakeTransport(OSError("connection reset"), (200, OK_BODY))
        response = client(transport, sleeps).send(request())
        self.assertEqual(response.text, "hi")
        self.assertEqual(len(transport.calls), 2)

    def test_a_client_error_is_not_retried(self) -> None:
        transport = FakeTransport((400, "bad request"), (200, OK_BODY))
        with self.assertRaises(ModelError) as caught:
            client(transport, []).send(request())
        self.assertIn("HTTP 400", str(caught.exception))
        self.assertEqual(len(transport.calls), 1)

    def test_giving_up_after_the_retries_raises_the_last_error(self) -> None:
        transport = FakeTransport((500, "a"), (500, "b"), (500, "c"))
        with self.assertRaises(ModelError) as caught:
            client(transport, [], max_retries=2).send(request())
        self.assertIn("HTTP 500", str(caught.exception))
        self.assertEqual(len(transport.calls), 3)

    def test_an_unknown_stop_reason_is_rejected(self) -> None:
        transport = FakeTransport((200, json.dumps({"content": [], "stop_reason": "surprise"})))
        with self.assertRaises(ModelError):
            client(transport, []).send(request())

    def test_a_malformed_reply_is_rejected(self) -> None:
        transport = FakeTransport((200, "not json"))
        with self.assertRaises(ModelError):
            client(transport, []).send(request())

    def test_the_model_id_and_key_are_required(self) -> None:
        with self.assertRaises(ValueError):
            AnthropicModelClient("", "m")
        with self.assertRaises(ValueError):
            AnthropicModelClient("k", "")

    def test_a_failing_provider_fails_the_agent_run_instead_of_crashing(self) -> None:
        transport = FakeTransport((500, "x"), (500, "x"), (500, "x"))
        agent = (
            Agent.builder("a")
            .model(client(transport, []))
            .instructions(Instructions.for_role("x"))
            .build()
        )
        result = agent.run("goal")
        self.assertIs(result.status, RunStatus.FAILED)
        self.assertIn("HTTP 500", result.note)


if __name__ == "__main__":
    unittest.main()
