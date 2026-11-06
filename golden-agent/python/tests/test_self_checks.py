"""Behaviour checks for the library. These are the 25 checks of the Java SelfCheck, ported one for one."""

from __future__ import annotations

import json
import unittest
from collections.abc import Mapping
from typing import Any

from golden_agent import (
    Agent,
    FunctionTool,
    Gate,
    Gates,
    InputSchema,
    Instructions,
    Message,
    ModelError,
    ModelRequest,
    RunStatus,
    ScriptedModelClient,
    StopReason,
    Text,
    ToolDefinition,
    ToolError,
    ToolOutput,
    ToolRegistry,
    ToolResult,
    ToolUse,
    TypedTool,
    all_of,
    allow_all,
    ask_human,
    deny,
    end_turn,
    stopped_with,
    tool_use,
)
from golden_agent.guard import Allow, AskHuman, Deny
from golden_agent.provider import build_body, parse_response


def base(name: str, model: Any) -> Any:
    return Agent.builder(name).model(model).instructions(Instructions.for_role("test agent"))


def echo() -> FunctionTool:
    return FunctionTool.of(
        ToolDefinition("echo", "Echo the text back", InputSchema.object().string("text", "Text").build()),
        lambda args: f"echo:{args['text']}",
    )


def blocks(result: Any) -> list[Any]:
    return [block for message in result.history for block in message.content]


def tool_results(result: Any) -> list[ToolResult]:
    return [b for b in blocks(result) if isinstance(b, ToolResult)]


class LoopTests(unittest.TestCase):
    def test_end_turn_completes_the_run(self) -> None:
        result = base("a", ScriptedModelClient(end_turn("done"))).build().run("goal")
        self.assertTrue(result.completed)
        self.assertEqual(result.text, "done")
        self.assertEqual(result.iterations, 1)

    def test_tool_use_runs_the_tool_and_returns_a_matching_tool_result(self) -> None:
        model = ScriptedModelClient(tool_use("t1", "echo", {"text": "hi"}), end_turn("ok"))
        result = base("a", model).tool(echo()).build().run("goal")
        second_request = model.requests[1]
        sent = [b for m in second_request.messages for b in m.content]
        self.assertTrue(result.completed)
        self.assertIn(ToolResult("t1", "echo:hi", False), sent)

    def test_iterations_equals_tool_calls_plus_one(self) -> None:
        model = ScriptedModelClient(
            tool_use("t1", "echo", {"text": "a"}), tool_use("t2", "echo", {"text": "b"}), end_turn("ok")
        )
        result = base("a", model).tool(echo()).build().run("goal")
        self.assertEqual(result.iterations, 3)
        self.assertEqual(len(tool_results(result)), 2)

    def test_unknown_tool_becomes_an_error_result_and_the_loop_continues(self) -> None:
        model = ScriptedModelClient(tool_use("t1", "nope", {}), end_turn("recovered"))
        result = base("a", model).tool(echo()).build().run("goal")
        errors = [r for r in tool_results(result) if r.is_error and "Unknown tool" in r.content]
        self.assertTrue(result.completed)
        self.assertEqual(len(errors), 1)

    def test_unexpected_tool_failure_becomes_an_error_result_without_leaking_internals(self) -> None:
        def explode(args: Mapping[str, Any]) -> str:
            raise RuntimeError("secret internals")

        boom = FunctionTool.of(ToolDefinition("boom", "Always fails", InputSchema.object().build()), explode)
        model = ScriptedModelClient(tool_use("t1", "boom", {}), end_turn("ok"))
        result = base("a", model).tool(boom).build().run("goal")
        safe = [r for r in tool_results(result) if r.is_error and "secret internals" not in r.content]
        self.assertTrue(result.completed)
        self.assertEqual(len(safe), 1)

    def test_iteration_cap_stops_the_run_as_limit_reached(self) -> None:
        model = ScriptedModelClient(
            tool_use("t1", "echo", {"text": "a"}), tool_use("t2", "echo", {"text": "b"}), end_turn("never")
        )
        result = base("a", model).tool(echo()).max_iterations(2).build().run("goal")
        self.assertIs(result.status, RunStatus.LIMIT_REACHED)
        self.assertEqual(model.calls, 2)

    def _not_finished(self, reason: StopReason) -> None:
        result = base("a", ScriptedModelClient(stopped_with(reason))).build().run("goal")
        self.assertIs(result.status, RunStatus.NOT_FINISHED)

    def test_stop_reason_max_tokens_is_not_a_normal_finish(self) -> None:
        self._not_finished(StopReason.MAX_TOKENS)

    def test_stop_reason_refusal_is_not_a_normal_finish(self) -> None:
        self._not_finished(StopReason.REFUSAL)

    def test_stop_reason_pause_turn_is_not_a_normal_finish(self) -> None:
        self._not_finished(StopReason.PAUSE_TURN)

    def test_a_model_failure_ends_the_run_as_failed(self) -> None:
        def failing(request: ModelRequest) -> Any:
            raise ModelError("overloaded")

        agent = Agent.builder("a").instructions(Instructions.for_role("x")).model(failing).build()
        result = agent.run("goal")
        self.assertIs(result.status, RunStatus.FAILED)
        self.assertIn("overloaded", result.note)


class GateTests(unittest.TestCase):
    def test_a_denying_gate_stops_the_tool_from_running(self) -> None:
        executed: list[int] = []

        def danger(args: Mapping[str, Any]) -> str:
            executed.append(1)
            return "ran"

        tool = FunctionTool.of(ToolDefinition("danger", "Does something risky", InputSchema.object().build()), danger)
        model = ScriptedModelClient(tool_use("t1", "danger", {}), end_turn("ok"))
        result = base("a", model).tool(tool).gate(lambda call, state: deny("not today")).build().run("goal")
        self.assertEqual(executed, [])
        self.assertTrue(result.completed)

    def test_a_prerequisite_gate_blocks_the_first_act_and_allows_it_after_verify(self) -> None:
        acted: list[int] = []
        verify = FunctionTool.of(ToolDefinition("verify", "Verify", InputSchema.object().build()), lambda a: "ok")

        def act_body(args: Mapping[str, Any]) -> str:
            acted.append(1)
            return "done"

        act = FunctionTool.of(ToolDefinition("act", "Act", InputSchema.object().build()), act_body)
        model = ScriptedModelClient(
            tool_use("t1", "act", {}), tool_use("t2", "verify", {}), tool_use("t3", "act", {}), end_turn("ok")
        )
        base("a", model).tools(verify, act).gate(Gates.prerequisites({"act": {"verify"}})).build().run("goal")
        self.assertEqual(len(acted), 1)

    def _pay_tool(self, executed: list[int]) -> FunctionTool:
        def pay(args: Mapping[str, Any]) -> str:
            executed.append(1)
            return "paid"

        return FunctionTool.of(ToolDefinition("pay", "Pay", InputSchema.object().build()), pay)

    def test_with_no_approver_an_ask_human_gate_denies_it_fails_closed(self) -> None:
        executed: list[int] = []
        model = ScriptedModelClient(tool_use("t1", "pay", {}), end_turn("ok"))
        base("a", model).tool(self._pay_tool(executed)).gate(
            Gates.require_approval({"pay"}, "needs approval")
        ).build().run("goal")
        self.assertEqual(executed, [])

    def test_an_approving_human_lets_the_tool_run(self) -> None:
        executed: list[int] = []
        model = ScriptedModelClient(tool_use("t1", "pay", {}), end_turn("ok"))
        base("a", model).tool(self._pay_tool(executed)).gate(
            Gates.require_approval({"pay"}, "needs approval")
        ).approver(lambda call, reason: True).build().run("goal")
        self.assertEqual(len(executed), 1)

    def test_combined_gates_return_the_first_non_allow_decision(self) -> None:
        allow = allow_all()
        denier: Gate = lambda c, s: deny("first")
        asker: Gate = lambda c, s: ask_human("second")
        call = ToolUse("1", "x", {})
        self.assertIsInstance(all_of(allow, denier, asker)(call, None), Deny)  # type: ignore[arg-type]
        self.assertIsInstance(all_of(allow, asker, denier)(call, None), AskHuman)  # type: ignore[arg-type]
        self.assertIsInstance(all_of(allow, allow)(call, None), Allow)  # type: ignore[arg-type]

    def test_a_result_filter_trims_output_before_the_model_sees_it(self) -> None:
        big = FunctionTool.of(ToolDefinition("big", "Big output", InputSchema.object().build()), lambda a: "x" * 1000)
        model = ScriptedModelClient(tool_use("t1", "big", {}), end_turn("ok"))
        result = (
            base("a", model)
            .tool(big)
            .result_filter(lambda call, out: ToolOutput(out.content[:10], out.is_error))
            .build()
            .run("goal")
        )
        self.assertTrue(any(len(r.content) == 10 for r in tool_results(result)))


class StateAndToolTests(unittest.TestCase):
    def test_the_full_history_grows_and_is_sent_on_every_call(self) -> None:
        model = ScriptedModelClient(tool_use("t1", "echo", {"text": "a"}), end_turn("ok"))
        base("a", model).tool(echo()).build().run("goal")
        self.assertEqual(len(model.requests[0].messages), 1)
        self.assertEqual(len(model.requests[1].messages), 3)

    def test_each_run_starts_with_a_fresh_state(self) -> None:
        agent = base("a", ScriptedModelClient(end_turn("one"), end_turn("two"))).build()
        first = agent.run("first goal")
        second = agent.run("second goal")
        self.assertEqual(len(first.history), 2)
        self.assertEqual(len(second.history), 2)
        self.assertEqual(second.history[0].content[0], Text("second goal"))

    def test_a_typed_tool_rejects_missing_arguments_and_decodes_valid_ones(self) -> None:
        class Hello(TypedTool[str, str]):
            def __init__(self) -> None:
                super().__init__(ToolDefinition("t", "Typed", InputSchema.object().string("name", "Name").build()))

            def decode(self, input: Mapping[str, Any]) -> str:
                return self.string(input, "name")

            def run(self, input: str) -> str:
                return f"hello {input}"

        tool = Hello()
        with self.assertRaises(ToolError) as caught:
            tool.execute({})
        self.assertIn("Missing required argument", str(caught.exception))
        self.assertEqual(tool.execute({"name": "Ada"}).content, "hello Ada")

    def test_a_registry_subset_restricts_tools_and_duplicates_are_rejected(self) -> None:
        everything = ToolRegistry.of(echo())
        none = everything.subset(set())
        self.assertEqual(none.names(), frozenset())
        self.assertIn("echo", everything.names())
        with self.assertRaises(ValueError):
            everything.register(echo())
        with self.assertRaises(ValueError):
            everything.subset({"missing"})


class MultiAgentTests(unittest.TestCase):
    def test_a_sub_agent_sees_only_the_task_and_context_it_was_given(self) -> None:
        sub_model = ScriptedModelClient(end_turn("sub result"))
        sub = base("worker", sub_model).description("Does work").build()
        coord_model = ScriptedModelClient(
            tool_use("c1", "delegate_to_worker", {"task": "do it", "context": "prior finding X"}), end_turn("final")
        )
        result = base("coord", coord_model).sub_agent(sub).build().run("secret coordinator goal")
        first_block = sub_model.requests[0].messages[0].content[0]
        seen = first_block.text if isinstance(first_block, Text) else ""
        self.assertTrue(result.completed)
        self.assertIn("do it", seen)
        self.assertIn("prior finding X", seen)
        self.assertNotIn("secret coordinator goal", seen)

    def test_a_failed_sub_agent_returns_a_structured_error_and_the_coordinator_continues(self) -> None:
        sub = base("worker", ScriptedModelClient(stopped_with(StopReason.MAX_TOKENS))).build()
        coord_model = ScriptedModelClient(
            tool_use("c1", "delegate_to_worker", {"task": "do it"}), end_turn("continued with a gap")
        )
        result = base("coord", coord_model).sub_agent(sub).build().run("goal")
        structured = [
            r
            for r in tool_results(result)
            if r.is_error and "SUB_AGENT_FAILURE" in r.content and "status=NOT_FINISHED" in r.content
        ]
        self.assertTrue(result.completed)
        self.assertEqual(len(structured), 1)


class UtilityTests(unittest.TestCase):
    def test_json_parses_writes_and_round_trips(self) -> None:
        text = '{"a":1,"b":[true,null,"x\\ny"],"c":{"d":2.5}}'
        parsed = json.loads(text)
        again = json.loads(json.dumps(parsed))
        self.assertEqual(parsed, again)
        self.assertEqual(again["a"], 1)
        self.assertIn("\\n", json.dumps(parsed))

    def test_the_anthropic_request_and_response_mapping_matches_the_api_shape(self) -> None:
        request = ModelRequest(
            "sys",
            (
                Message.user("hi"),
                Message.assistant([ToolUse("t1", "echo", {"text": "a"})]),
                Message.tool_results([ToolResult("t1", "echo:a", False)]),
            ),
            (echo().definition(),),
            256,
        )
        body = build_body("some-model", request)
        self.assertEqual(body["model"], "some-model")
        self.assertEqual(body["max_tokens"], 256)
        self.assertEqual(body["system"], "sys")
        self.assertEqual(len(body["messages"]), 3)
        self.assertEqual(len(body["tools"]), 1)
        self.assertEqual(body["messages"][1]["content"][0]["type"], "tool_use")
        self.assertEqual(body["messages"][2]["content"][0]["tool_use_id"], "t1")
        json.dumps(body)  # must be serialisable

        reply = (
            '{"content":[{"type":"text","text":"hello"},'
            '{"type":"tool_use","id":"t9","name":"echo","input":{"text":"z"}}],"stop_reason":"tool_use"}'
        )
        response = parse_response(reply)
        self.assertIs(response.stop_reason, StopReason.TOOL_USE)
        self.assertEqual(len(response.tool_uses), 1)
        self.assertEqual(response.text, "hello")
        with self.assertRaises(ModelError):
            parse_response('{"content":[],"stop_reason":"surprise"}')

    def test_instructions_compose_and_render_role_rules_and_format(self) -> None:
        shared = Instructions.for_role("Be careful.").with_rule("Never guess.")
        specific = Instructions.for_role("You research.").with_rule("Cite sources.").with_output_format("Bullets")
        text = shared.and_(specific).render()
        self.assertIn("Never guess.", text)
        self.assertIn("Cite sources.", text)
        self.assertIn("Bullets", text)


if __name__ == "__main__":
    unittest.main()
