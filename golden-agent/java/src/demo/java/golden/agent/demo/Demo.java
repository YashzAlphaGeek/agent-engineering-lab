package golden.agent.demo;

import golden.agent.composition.Agent;
import golden.agent.guard.Gates;
import golden.agent.instructions.Instructions;
import golden.agent.loop.AgentResult;
import golden.agent.model.ContentBlock;
import golden.agent.model.Message;
import golden.agent.model.ScriptedModelClient;
import golden.agent.tool.FunctionTool;
import golden.agent.tool.InputSchema;
import golden.agent.tool.ToolDefinition;
import golden.agent.tool.ToolException;
import golden.agent.tool.TypedTool;

import java.util.Map;
import java.util.Set;

import static golden.agent.model.ScriptedModelClient.endTurn;
import static golden.agent.model.ScriptedModelClient.toolUse;

/**
 * Two runnable examples that need no network and no API key. A scripted model plays the role of the LLM,
 * so the output is the same every time. Replace {@code ScriptedModelClient} with
 * {@code AnthropicModelClient} (or your own {@code ModelClient}) to run against a real model.
 */
public final class Demo {

    public static void main(String[] args) {
        singleAgentWithGates();
        System.out.println();
        coordinatorWithSubAgents();
    }

    // Example 1: one agent, typed tools, a prerequisite gate and a human approval gate

    record RefundRequest(String orderId, int amountCents) { }

    static final class GetCustomerTool extends TypedTool<String, String> {
        GetCustomerTool() {
            super(new ToolDefinition(
                    "get_customer",
                    "Look up and verify a customer by id. Use before any account change. Returns the verification result.",
                    InputSchema.object().string("customerId", "The customer id, for example C-1", true).build()));
        }

        @Override protected String decode(Map<String, Object> input) throws ToolException {
            return string(input, "customerId");
        }

        @Override protected String run(String customerId) {
            return "customer " + customerId + " is verified";
        }
    }

    static final class RefundTool extends TypedTool<RefundRequest, String> {
        RefundTool() {
            super(new ToolDefinition(
                    "process_refund",
                    "Refund an order. Requires a verified customer. Returns a confirmation.",
                    InputSchema.object()
                            .string("orderId", "The order id", true)
                            .integer("amountCents", "Refund amount in cents", true)
                            .build()));
        }

        @Override protected RefundRequest decode(Map<String, Object> input) throws ToolException {
            return new RefundRequest(string(input, "orderId"), integer(input, "amountCents"));
        }

        @Override protected String run(RefundRequest r) {
            return "refunded " + r.amountCents() + " cents for order " + r.orderId();
        }
    }

    static void singleAgentWithGates() {
        System.out.println("=== Example 1: one agent with gates ===");

        ScriptedModelClient model = new ScriptedModelClient(
                // The model tries to refund before verifying the customer. The gate blocks it.
                toolUse("t1", "process_refund", Map.of("orderId", "O-1", "amountCents", 9000)),
                toolUse("t2", "get_customer", Map.of("customerId", "C-1")),
                // Now allowed by the prerequisite gate, but the approval gate asks a human.
                toolUse("t3", "process_refund", Map.of("orderId", "O-1", "amountCents", 9000)),
                endTurn("The refund of 9000 cents for order O-1 has been processed."));

        Agent support = Agent.builder("support")
                .description("Handles customer refund requests")
                .model(model)
                .instructions(Instructions.forRole("You are a customer support agent.")
                        .withRule("Verify the customer before changing an account.")
                        .withOutputFormat("One short confirmation sentence."))
                .tools(new GetCustomerTool(), new RefundTool())
                .gate(Gates.prerequisites(Map.of("process_refund", Set.of("get_customer"))))
                .gate(Gates.requireApproval(Set.of("process_refund"), "refunds need human approval"))
                .approver((call, reason) -> {
                    System.out.println("  [human] approving " + call.name() + " because: " + reason);
                    return true;
                })
                .maxIterations(10)
                .build();

        print(support.run("Refund order O-1 for customer C-1, 9000 cents"));
    }

    // Example 2: a coordinator that delegates to two sub agents, each with its own tools

    static void coordinatorWithSubAgents() {
        System.out.println("=== Example 2: coordinator with sub agents ===");

        Agent researcher = Agent.builder("researcher")
                .description("Finds facts on a topic and returns claims with their sources")
                .model(new ScriptedModelClient(
                        toolUse("r1", "web_search", Map.of("query", "java jobs munich")),
                        endTurn("Claim: 12 Java roles in Munich. Source: example-jobs.test/munich")))
                .instructions(Instructions.forRole("You are a research agent.")
                        .withRule("Always return each claim with its source."))
                .tool(FunctionTool.of(
                        new ToolDefinition("web_search", "Search the web. Returns a short list of results.",
                                InputSchema.object().string("query", "Search query", true).build()),
                        in -> "12 results for " + in.get("query")))
                .build();

        Agent analyst = Agent.builder("analyst")
                .description("Analyses findings and returns strengths and gaps")
                .model(new ScriptedModelClient(
                        endTurn("Gap: Spring Boot experience. Strength: Java fundamentals.")))
                .instructions(Instructions.forRole("You are an analysis agent."))
                .build();

        Agent coordinator = Agent.builder("coordinator")
                .description("Plans, delegates and aggregates")
                .model(new ScriptedModelClient(
                        toolUse("c1", "delegate_to_researcher",
                                Map.of("task", "Find Java jobs in Munich", "context", "Goal: job search")),
                        toolUse("c2", "delegate_to_analyst",
                                Map.of("task", "Analyse the findings",
                                        "context", "Finding: 12 Java roles in Munich (example-jobs.test/munich)")),
                        endTurn("Report: 12 roles found. Gap: Spring Boot. Sources: example-jobs.test/munich")))
                .instructions(Instructions.forRole("You are a coordinator.")
                        .withRule("Pass each sub agent all the context it needs."))
                .subAgent(researcher)
                .subAgent(analyst)
                .build();

        print(coordinator.run("Find Java jobs in Munich and analyse my skills"));
    }

    // Printing

    private static void print(AgentResult result) {
        for (Message message : result.history()) {
            for (ContentBlock block : message.content()) {
                switch (block) {
                    case ContentBlock.Text t -> System.out.println("  " + message.role() + " says: " + t.text());
                    case ContentBlock.ToolUse u -> System.out.println("  model asks for " + u.name() + " " + u.input());
                    case ContentBlock.ToolResult r ->
                            System.out.println("  tool_result" + (r.isError() ? " (error)" : "") + ": " + r.content());
                }
            }
        }
        System.out.println("  -> status=" + result.status() + " iterations=" + result.iterations()
                + " lastStopReason=" + result.lastStopReason());
    }
}
