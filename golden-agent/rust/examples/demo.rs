//! Two runnable examples that need no network and no API key. A scripted model plays the role of the LLM, so
//! the output is the same every time. Replace `ScriptedModelClient` with an `AnthropicModelClient` (or your own
//! `ModelClient`) to run against a real model.
//!
//! Run with: `cargo run --example demo`

use std::sync::Arc;

use golden_agent::prelude::*;

// Example 1: one agent, typed tools, a prerequisite gate and a human approval gate

struct RefundRequest {
    order_id: String,
    amount_cents: i64,
}

fn get_customer_tool() -> TypedTool<String, String> {
    TypedTool::displaying(
        ToolDefinition::new(
            "get_customer",
            "Look up and verify a customer by id. Use before any account change. Returns the verification result.",
            InputSchema::object()
                .string("customerId", "The customer id, for example C-1", true)
                .build(),
        )
        .expect("valid definition"),
        |input| string_arg(input, "customerId"),
        |customer_id| Ok(format!("customer {customer_id} is verified")),
    )
}

fn refund_tool() -> TypedTool<RefundRequest, String> {
    TypedTool::displaying(
        ToolDefinition::new(
            "process_refund",
            "Refund an order. Requires a verified customer. Returns a confirmation.",
            InputSchema::object()
                .string("orderId", "The order id", true)
                .integer("amountCents", "Refund amount in cents", true)
                .build(),
        )
        .expect("valid definition"),
        |input| {
            Ok(RefundRequest {
                order_id: string_arg(input, "orderId")?,
                amount_cents: integer_arg(input, "amountCents")?,
            })
        },
        |r| {
            Ok(format!(
                "refunded {} cents for order {}",
                r.amount_cents, r.order_id
            ))
        },
    )
}

fn single_agent_with_gates() {
    println!("=== Example 1: one agent with gates ===");

    let refund_args = || {
        tool_input([
            ("orderId", Json::from("O-1")),
            ("amountCents", Json::from(9000)),
        ])
    };
    let model = ScriptedModelClient::new([
        // The model tries to refund before verifying the customer. The gate blocks it.
        ScriptedModelClient::tool_use("t1", "process_refund", refund_args()),
        ScriptedModelClient::tool_use("t2", "get_customer", tool_input([("customerId", "C-1")])),
        // Now allowed by the prerequisite gate, but the approval gate asks a human.
        ScriptedModelClient::tool_use("t3", "process_refund", refund_args()),
        ScriptedModelClient::end_turn("The refund of 9000 cents for order O-1 has been processed."),
    ]);

    let support = Agent::builder("support")
        .description("Handles customer refund requests")
        .model(model)
        .instructions(
            Instructions::for_role("You are a customer support agent.")
                .with_rule("Verify the customer before changing an account.")
                .with_output_format("One short confirmation sentence."),
        )
        .tool(get_customer_tool())
        .tool(refund_tool())
        .gate(Gates::prerequisites(&[(
            "process_refund",
            &["get_customer"],
        )]))
        .gate(Gates::require_approval(
            &["process_refund"],
            "refunds need human approval",
        ))
        .approver(HumanApprover::new(|call, reason| {
            println!("  [human] approving {} because: {reason}", call.name);
            true
        }))
        .max_iterations(10)
        .build()
        .expect("agent builds");

    print_result(&support.run("Refund order O-1 for customer C-1, 9000 cents"));
}

// Example 2: a coordinator that delegates to two sub agents, each with its own tools

fn coordinator_with_sub_agents() {
    println!("=== Example 2: coordinator with sub agents ===");

    let web_search = FunctionTool::new(
        ToolDefinition::new(
            "web_search",
            "Search the web. Returns a short list of results.",
            InputSchema::object()
                .string("query", "Search query", true)
                .build(),
        )
        .expect("valid definition"),
        |input| format!("12 results for {}", input["query"].as_str().unwrap_or("")),
    );

    let researcher = Arc::new(
        Agent::builder("researcher")
            .description("Finds facts on a topic and returns claims with their sources")
            .model(ScriptedModelClient::new([
                ScriptedModelClient::tool_use(
                    "r1",
                    "web_search",
                    tool_input([("query", "java jobs munich")]),
                ),
                ScriptedModelClient::end_turn(
                    "Claim: 12 Java roles in Munich. Source: example-jobs.test/munich",
                ),
            ]))
            .instructions(
                Instructions::for_role("You are a research agent.")
                    .with_rule("Always return each claim with its source."),
            )
            .tool(web_search)
            .build()
            .expect("agent builds"),
    );

    let analyst = Arc::new(
        Agent::builder("analyst")
            .description("Analyses findings and returns strengths and gaps")
            .model(ScriptedModelClient::new([ScriptedModelClient::end_turn(
                "Gap: Spring Boot experience. Strength: Java fundamentals.",
            )]))
            .instructions(Instructions::for_role("You are an analysis agent."))
            .build()
            .expect("agent builds"),
    );

    let coordinator = Agent::builder("coordinator")
        .description("Plans, delegates and aggregates")
        .model(ScriptedModelClient::new([
            ScriptedModelClient::tool_use(
                "c1",
                "delegate_to_researcher",
                tool_input([
                    ("task", "Find Java jobs in Munich"),
                    ("context", "Goal: job search"),
                ]),
            ),
            ScriptedModelClient::tool_use(
                "c2",
                "delegate_to_analyst",
                tool_input([
                    ("task", "Analyse the findings"),
                    (
                        "context",
                        "Finding: 12 Java roles in Munich (example-jobs.test/munich)",
                    ),
                ]),
            ),
            ScriptedModelClient::end_turn(
                "Report: 12 roles found. Gap: Spring Boot. Sources: example-jobs.test/munich",
            ),
        ]))
        .instructions(
            Instructions::for_role("You are a coordinator.")
                .with_rule("Pass each sub agent all the context it needs."),
        )
        .sub_agent(researcher)
        .sub_agent(analyst)
        .build()
        .expect("agent builds");

    print_result(&coordinator.run("Find Java jobs in Munich and analyse my skills"));
}

// Printing

fn print_result(result: &AgentResult) {
    for message in &result.history {
        let who = match message.role {
            Role::User => "USER",
            Role::Assistant => "ASSISTANT",
        };
        for block in &message.content {
            match block {
                ContentBlock::Text(text) => println!("  {who} says: {text}"),
                ContentBlock::ToolUse(u) => {
                    let args: Vec<String> = u
                        .input
                        .iter()
                        .map(|(k, v)| match v.as_str() {
                            Some(text) => format!("{k}={text}"),
                            None => format!("{k}={v}"),
                        })
                        .collect();
                    println!("  model asks for {} {{{}}}", u.name, args.join(", "));
                }
                ContentBlock::ToolResult(r) => {
                    let marker = if r.is_error { " (error)" } else { "" };
                    println!("  tool_result{marker}: {}", r.content);
                }
            }
        }
    }
    let last = result
        .last_stop_reason
        .map_or_else(|| "none".to_string(), |r| r.to_string());
    println!(
        "  -> status={} iterations={} lastStopReason={last}",
        result.status, result.iterations
    );
}

fn main() {
    single_agent_with_gates();
    println!();
    coordinator_with_sub_agents();
}
