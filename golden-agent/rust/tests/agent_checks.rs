//! Ports of the 25 self-checks from the Java library, one test per check.

use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};

use golden_agent::prelude::*;
use golden_agent::provider::anthropic::{build_body, parse_response};
use golden_agent::tool::RegistryError;

// Helpers

fn base(name: &str, model: Arc<ScriptedModelClient>) -> AgentBuilder {
    Agent::builder(name)
        .model(model)
        .instructions(Instructions::for_role("test agent"))
}

fn definition(name: &str, description: &str, schema: InputSchema) -> ToolDefinition {
    ToolDefinition::new(name, description, schema).unwrap()
}

fn echo() -> FunctionTool {
    FunctionTool::new(
        definition(
            "echo",
            "Echo the text back",
            InputSchema::object().string("text", "Text", true).build(),
        ),
        |input| format!("echo:{}", input["text"].as_str().unwrap_or("")),
    )
}

fn no_args_tool(name: &str, body: impl Fn() -> String + Send + Sync + 'static) -> FunctionTool {
    FunctionTool::new(
        definition(name, "Test tool", InputSchema::object().build()),
        move |_| body(),
    )
}

fn call(name: &str) -> ScriptedStep {
    ScriptedModelClient::tool_use("t", name, ToolInput::new())
}

type ScriptedStep = ModelResponse;

fn results(result: &AgentResult) -> Vec<&ToolResult> {
    result
        .history
        .iter()
        .flat_map(|m| m.content.iter())
        .filter_map(|b| match b {
            ContentBlock::ToolResult(r) => Some(r),
            _ => None,
        })
        .collect()
}

// Loop

#[test]
fn check_01_end_turn_completes_the_run() {
    let model = Arc::new(ScriptedModelClient::new([ScriptedModelClient::end_turn(
        "done",
    )]));
    let r = base("a", model).build().unwrap().run("goal");
    assert!(r.completed() && r.text == "done" && r.iterations == 1);
}

#[test]
fn check_02_tool_use_runs_the_tool_and_returns_a_matching_tool_result() {
    let model = Arc::new(ScriptedModelClient::new([
        ScriptedModelClient::tool_use("t1", "echo", tool_input([("text", "hi")])),
        ScriptedModelClient::end_turn("ok"),
    ]));
    let r = base("a", model.clone())
        .tool(echo())
        .build()
        .unwrap()
        .run("goal");
    let second_request = &model.requests()[1];
    let sent = second_request.messages.iter().flat_map(|m| m.content.iter()).any(|b| {
        matches!(b, ContentBlock::ToolResult(tr) if tr.tool_use_id == "t1" && tr.content == "echo:hi")
    });
    assert!(r.completed() && sent);
}

#[test]
fn check_03_iterations_equals_tool_calls_plus_one() {
    let model = Arc::new(ScriptedModelClient::new([
        ScriptedModelClient::tool_use("t1", "echo", tool_input([("text", "a")])),
        ScriptedModelClient::tool_use("t2", "echo", tool_input([("text", "b")])),
        ScriptedModelClient::end_turn("ok"),
    ]));
    let r = base("a", model).tool(echo()).build().unwrap().run("goal");
    assert!(r.iterations == 3 && results(&r).len() == 2);
}

#[test]
fn check_04_unknown_tool_becomes_an_error_result_and_the_loop_continues() {
    let model = Arc::new(ScriptedModelClient::new([
        call("nope"),
        ScriptedModelClient::end_turn("recovered"),
    ]));
    let r = base("a", model).tool(echo()).build().unwrap().run("goal");
    let errored = results(&r)
        .iter()
        .any(|tr| tr.is_error && tr.content.contains("Unknown tool"));
    assert!(r.completed() && errored);
}

#[test]
fn check_05_unexpected_tool_failure_becomes_an_error_result_without_leaking_internals() {
    let boom = no_args_tool("boom", || panic!("secret internals"));
    let model = Arc::new(ScriptedModelClient::new([
        call("boom"),
        ScriptedModelClient::end_turn("ok"),
    ]));
    let r = base("a", model).tool(boom).build().unwrap().run("goal");
    let safe = results(&r).iter().any(|tr| {
        tr.is_error
            && !tr.content.contains("secret internals")
            && tr.content.contains("failed unexpectedly")
    });
    assert!(r.completed() && safe);
}

#[test]
fn check_06_iteration_cap_stops_the_run_as_limit_reached() {
    let model = Arc::new(ScriptedModelClient::new([
        ScriptedModelClient::tool_use("t1", "echo", tool_input([("text", "a")])),
        ScriptedModelClient::tool_use("t2", "echo", tool_input([("text", "b")])),
        ScriptedModelClient::end_turn("never reached"),
    ]));
    let r = base("a", model.clone())
        .tool(echo())
        .max_iterations(2)
        .build()
        .unwrap()
        .run("goal");
    assert!(r.status == RunStatus::LimitReached && model.calls() == 2);
}

fn not_finished(reason: StopReason) {
    let model = Arc::new(ScriptedModelClient::new([
        ScriptedModelClient::stopped_with(reason),
    ]));
    let r = base("a", model).build().unwrap().run("goal");
    assert_eq!(
        r.status,
        RunStatus::NotFinished,
        "stop reason {reason} must not be a normal finish"
    );
}

#[test]
fn check_07_max_tokens_is_not_a_normal_finish() {
    not_finished(StopReason::MaxTokens);
}

#[test]
fn check_08_refusal_is_not_a_normal_finish() {
    not_finished(StopReason::Refusal);
}

#[test]
fn check_09_pause_turn_is_not_a_normal_finish() {
    not_finished(StopReason::PauseTurn);
}

#[test]
fn check_10_a_model_failure_ends_the_run_as_failed() {
    let agent = Agent::builder("a")
        .instructions(Instructions::for_role("x"))
        .model(|_: &ModelRequest| Err(ModelError::new("overloaded")))
        .build()
        .unwrap();
    let r = agent.run("goal");
    assert!(r.status == RunStatus::Failed && r.note.contains("overloaded"));
}

// Gates

#[test]
fn check_11_a_denying_gate_stops_the_tool_from_running() {
    let executed = Arc::new(AtomicUsize::new(0));
    let counter = executed.clone();
    let danger = no_args_tool("danger", move || {
        counter.fetch_add(1, Ordering::SeqCst);
        "ran".to_string()
    });
    let model = Arc::new(ScriptedModelClient::new([
        call("danger"),
        ScriptedModelClient::end_turn("ok"),
    ]));
    let r = base("a", model)
        .tool(danger)
        .gate(Gate::new(|_, _| Decision::Deny("not today".to_string())))
        .build()
        .unwrap()
        .run("goal");
    assert!(executed.load(Ordering::SeqCst) == 0 && r.completed());
    assert!(
        results(&r)
            .iter()
            .any(|tr| tr.is_error && tr.content.contains("not today"))
    );
}

#[test]
fn check_12_a_prerequisite_gate_blocks_the_first_act_and_allows_it_after_verify() {
    let acted = Arc::new(AtomicUsize::new(0));
    let counter = acted.clone();
    let verify = no_args_tool("verify", || "ok".to_string());
    let act = no_args_tool("act", move || {
        counter.fetch_add(1, Ordering::SeqCst);
        "done".to_string()
    });
    let model = Arc::new(ScriptedModelClient::new([
        call("act"),
        call("verify"),
        call("act"),
        ScriptedModelClient::end_turn("ok"),
    ]));
    base("a", model)
        .tool(verify)
        .tool(act)
        .gate(Gates::prerequisites(&[("act", &["verify"])]))
        .build()
        .unwrap()
        .run("goal");
    assert_eq!(acted.load(Ordering::SeqCst), 1);
}

fn pay_counter() -> (FunctionTool, Arc<AtomicUsize>) {
    let executed = Arc::new(AtomicUsize::new(0));
    let counter = executed.clone();
    let pay = no_args_tool("pay", move || {
        counter.fetch_add(1, Ordering::SeqCst);
        "paid".to_string()
    });
    (pay, executed)
}

#[test]
fn check_13_with_no_approver_an_ask_human_gate_denies_it_fails_closed() {
    let (pay, executed) = pay_counter();
    let model = Arc::new(ScriptedModelClient::new([
        call("pay"),
        ScriptedModelClient::end_turn("ok"),
    ]));
    base("a", model)
        .tool(pay)
        .gate(Gates::require_approval(&["pay"], "needs approval"))
        .build()
        .unwrap()
        .run("goal");
    assert_eq!(executed.load(Ordering::SeqCst), 0);
}

#[test]
fn check_14_an_approving_human_lets_the_tool_run() {
    let (pay, executed) = pay_counter();
    let model = Arc::new(ScriptedModelClient::new([
        call("pay"),
        ScriptedModelClient::end_turn("ok"),
    ]));
    base("a", model)
        .tool(pay)
        .gate(Gates::require_approval(&["pay"], "needs approval"))
        .approver(HumanApprover::new(|_, _| true))
        .build()
        .unwrap()
        .run("goal");
    assert_eq!(executed.load(Ordering::SeqCst), 1);
}

#[test]
fn check_15_combined_gates_return_the_first_non_allow_decision() {
    let deny = || Gate::new(|_, _| Decision::Deny("first".to_string()));
    let ask = || Gate::new(|_, _| Decision::AskHuman("second".to_string()));
    let request = ToolUse {
        id: "1".into(),
        name: "x".into(),
        input: ToolInput::new(),
    };
    let state = AgentState::start("goal");
    let deny_first = Gate::allow_all()
        .and(deny())
        .and(ask())
        .check(&request, &state);
    let ask_first = Gate::allow_all()
        .and(ask())
        .and(deny())
        .check(&request, &state);
    assert!(matches!(deny_first, Decision::Deny(_)) && matches!(ask_first, Decision::AskHuman(_)));
}

#[test]
fn check_16_a_result_filter_trims_output_before_the_model_sees_it() {
    let big = no_args_tool("big", || "x".repeat(1000));
    let model = Arc::new(ScriptedModelClient::new([
        call("big"),
        ScriptedModelClient::end_turn("ok"),
    ]));
    let r = base("a", model)
        .tool(big)
        .result_filter(ResultFilter::new(|_, out| ToolOutput {
            content: out.content.chars().take(10).collect(),
            is_error: out.is_error,
        }))
        .build()
        .unwrap()
        .run("goal");
    assert!(results(&r).iter().any(|tr| tr.content.len() == 10));
}

// State and context

#[test]
fn check_17_the_full_history_grows_and_is_sent_on_every_call() {
    let model = Arc::new(ScriptedModelClient::new([
        ScriptedModelClient::tool_use("t1", "echo", tool_input([("text", "a")])),
        ScriptedModelClient::end_turn("ok"),
    ]));
    base("a", model.clone())
        .tool(echo())
        .build()
        .unwrap()
        .run("goal");
    let requests = model.requests();
    assert!(requests[0].messages.len() == 1 && requests[1].messages.len() == 3);
}

#[test]
fn check_18_each_run_starts_with_a_fresh_state() {
    let model = Arc::new(ScriptedModelClient::new([
        ScriptedModelClient::end_turn("one"),
        ScriptedModelClient::end_turn("two"),
    ]));
    let agent = base("a", model).build().unwrap();
    let first = agent.run("first goal");
    let second = agent.run("second goal");
    let opening = match &second.history[0].content[0] {
        ContentBlock::Text(t) => t.as_str(),
        _ => "",
    };
    assert!(first.history.len() == 2 && second.history.len() == 2 && opening == "second goal");
}

// Tools and registry

#[test]
fn check_19_a_typed_tool_rejects_missing_arguments_and_decodes_valid_ones() {
    let typed: TypedTool<String, String> = TypedTool::displaying(
        definition(
            "t",
            "Typed",
            InputSchema::object().string("name", "Name", true).build(),
        ),
        |input| string_arg(input, "name"),
        |name| Ok(format!("hello {name}")),
    );
    let rejected = typed
        .execute(&ToolInput::new())
        .is_err_and(|e| e.message.contains("Missing required argument"));
    let accepted = typed
        .execute(&tool_input([("name", "Ada")]))
        .is_ok_and(|out| out.content == "hello Ada");
    assert!(rejected && accepted);
}

#[test]
fn check_20_a_registry_subset_restricts_tools_and_duplicates_are_rejected() {
    let mut all = ToolRegistry::new();
    all.register(Arc::new(echo())).unwrap();
    let none = all.subset(&[]).unwrap();
    let duplicate = all.register(Arc::new(echo()));
    assert!(none.is_empty() && all.names() == vec!["echo".to_string()]);
    assert_eq!(duplicate, Err(RegistryError::Duplicate("echo".to_string())));
    assert!(matches!(
        all.subset(&["missing"]),
        Err(RegistryError::Unknown(_))
    ));
}

// Multi-agent

#[test]
fn check_21_a_sub_agent_sees_only_the_task_and_context_it_was_given() {
    let sub_model = Arc::new(ScriptedModelClient::new([ScriptedModelClient::end_turn(
        "sub result",
    )]));
    let sub = Arc::new(
        base("worker", sub_model.clone())
            .description("Does work")
            .build()
            .unwrap(),
    );
    let coord_model = Arc::new(ScriptedModelClient::new([
        ScriptedModelClient::tool_use(
            "c1",
            "delegate_to_worker",
            tool_input([("task", "do it"), ("context", "prior finding X")]),
        ),
        ScriptedModelClient::end_turn("final"),
    ]));
    let r = base("coord", coord_model)
        .sub_agent(sub)
        .build()
        .unwrap()
        .run("secret coordinator goal");

    let saw = match &sub_model.requests()[0].messages[0].content[0] {
        ContentBlock::Text(t) => t.clone(),
        _ => String::new(),
    };
    assert!(r.completed());
    assert!(saw.contains("do it") && saw.contains("prior finding X"));
    assert!(!saw.contains("secret coordinator goal"));
}

#[test]
fn check_22_a_failed_sub_agent_returns_a_structured_error_and_the_coordinator_continues() {
    let sub_model = Arc::new(ScriptedModelClient::new([
        ScriptedModelClient::stopped_with(StopReason::MaxTokens),
    ]));
    let sub = Arc::new(base("worker", sub_model).build().unwrap());
    let coord_model = Arc::new(ScriptedModelClient::new([
        ScriptedModelClient::tool_use("c1", "delegate_to_worker", tool_input([("task", "do it")])),
        ScriptedModelClient::end_turn("continued with a gap"),
    ]));
    let r = base("coord", coord_model)
        .sub_agent(sub)
        .build()
        .unwrap()
        .run("goal");
    let structured = results(&r).iter().any(|tr| {
        tr.is_error
            && tr.content.contains("SUB_AGENT_FAILURE")
            && tr.content.contains("status=NOT_FINISHED")
    });
    assert!(r.completed() && structured);
}

// Provider and text utilities

#[test]
fn check_23_json_parses_writes_and_round_trips() {
    let text = r#"{"a":1,"b":[true,null,"x\ny"],"c":{"d":2.5}}"#;
    let parsed = Json::parse(text).unwrap();
    let written = parsed.write();
    let again = Json::parse(&written).unwrap();
    assert_eq!(parsed, again);
    assert_eq!(again.get("a").and_then(Json::as_i64), Some(1));
    assert!(written.contains("\\n"));
}

#[test]
fn check_24_the_anthropic_request_and_response_mapping_matches_the_api_shape() {
    let echo_def = echo().definition().clone();
    let request = ModelRequest::new(
        "sys",
        vec![
            Message::user("hi"),
            Message::assistant(vec![ContentBlock::ToolUse(ToolUse {
                id: "t1".into(),
                name: "echo".into(),
                input: tool_input([("text", "a")]),
            })]),
            Message::tool_results(vec![ToolResult {
                tool_use_id: "t1".into(),
                content: "echo:a".into(),
                is_error: false,
            }]),
        ],
        vec![echo_def],
        256,
    );
    let body = Json::parse(&build_body("some-model", &request)).unwrap();
    let body_ok = body.get("model").and_then(Json::as_str) == Some("some-model")
        && body.get("max_tokens").and_then(Json::as_i64) == Some(256)
        && body.get("system").and_then(Json::as_str) == Some("sys")
        && body
            .get("messages")
            .and_then(Json::as_array)
            .map(<[Json]>::len)
            == Some(3)
        && body
            .get("tools")
            .and_then(Json::as_array)
            .map(<[Json]>::len)
            == Some(1);

    let reply = r#"{"content":[{"type":"text","text":"hello"},
        {"type":"tool_use","id":"t9","name":"echo","input":{"text":"z"}}],"stop_reason":"tool_use"}"#;
    let response = parse_response(reply).unwrap();
    let parsed_ok = response.stop_reason == StopReason::ToolUse
        && response.tool_uses().len() == 1
        && response.text() == "hello";

    let unknown_rejected = parse_response(r#"{"content":[],"stop_reason":"surprise"}"#).is_err();
    assert!(body_ok && parsed_ok && unknown_rejected);
}

#[test]
fn check_25_instructions_compose_and_render_role_rules_and_format() {
    let shared = Instructions::for_role("Be careful.").with_rule("Never guess.");
    let specific = Instructions::for_role("You research.")
        .with_rule("Cite sources.")
        .with_output_format("Bullets");
    let text = shared.and(&specific).render();
    assert!(
        text.contains("Never guess.") && text.contains("Cite sources.") && text.contains("Bullets")
    );
}
