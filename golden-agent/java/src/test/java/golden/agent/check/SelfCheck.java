package golden.agent.check;

import golden.agent.composition.Agent;
import golden.agent.guard.Decision;
import golden.agent.guard.Gate;
import golden.agent.guard.Gates;
import golden.agent.instructions.Instructions;
import golden.agent.loop.AgentResult;
import golden.agent.model.ContentBlock;
import golden.agent.model.Message;
import golden.agent.model.ModelException;
import golden.agent.model.ModelRequest;
import golden.agent.model.ScriptedModelClient;
import golden.agent.model.StopReason;
import golden.agent.provider.Json;
import golden.agent.state.RunStatus;
import golden.agent.tool.FunctionTool;
import golden.agent.tool.InputSchema;
import golden.agent.tool.Tool;
import golden.agent.tool.ToolDefinition;
import golden.agent.tool.ToolException;
import golden.agent.tool.ToolRegistry;
import golden.agent.tool.TypedTool;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static golden.agent.model.ScriptedModelClient.endTurn;
import static golden.agent.model.ScriptedModelClient.stoppedWith;
import static golden.agent.model.ScriptedModelClient.toolUse;

/**
 * Plain Java self-checks, with no test framework. Run it as a program: it prints each result and exits
 * with a non-zero code if anything fails.
 */
public final class SelfCheck {

    private static int failures;
    private static int total;

    public static void main(String[] args) throws Exception {
        loopFinishesOnEndTurn();
        toolUseRunsToolThenFinishes();
        sequentialToolCallsCountIterations();
        unknownToolBecomesErrorResultAndLoopContinues();
        toolFailureBecomesErrorResult();
        iterationCapIsAGuardRailNotAFinish();
        nonEndTurnStopReasonsAreNotFinished();
        modelFailureFailsTheRun();
        gateDenyBlocksTheTool();
        prerequisiteGateEnforcesOrder();
        askHumanFailsClosedByDefault();
        askHumanRunsWhenApproved();
        gatesCombineFirstNonAllowWins();
        resultFilterShrinksOutput();
        historyIsSentInFullOnEveryCall();
        eachRunHasIsolatedState();
        typedToolValidatesRequiredArguments();
        registrySubsetGivesLeastPrivilege();
        subAgentReceivesOnlyExplicitContext();
        subAgentFailureIsAStructuredError();
        jsonRoundTrip();
        anthropicMappingMatchesTheApiShape();
        instructionsComposeAndRender();

        System.out.println();
        System.out.println((total - failures) + " of " + total + " checks passed");
        if (failures > 0) {
            System.exit(1);
        }
    }

    // Helpers

    private static void check(String name, boolean condition) {
        total++;
        if (!condition) {
            failures++;
        }
        System.out.println((condition ? "  PASS  " : "  FAIL  ") + name);
    }

    private static Agent.Builder base(String name, ScriptedModelClient model) {
        return Agent.builder(name).model(model).instructions(Instructions.forRole("test agent"));
    }

    private static Tool echo() {
        return FunctionTool.of(
                new ToolDefinition("echo", "Echo the text back",
                        InputSchema.object().string("text", "Text", true).build()),
                in -> "echo:" + in.get("text"));
    }

    // Loop

    private static void loopFinishesOnEndTurn() {
        ScriptedModelClient model = new ScriptedModelClient(endTurn("done"));
        AgentResult r = base("a", model).build().run("goal");
        check("end_turn completes the run", r.completed() && r.text().equals("done") && r.iterations() == 1);
    }

    private static void toolUseRunsToolThenFinishes() {
        ScriptedModelClient model = new ScriptedModelClient(
                toolUse("t1", "echo", Map.of("text", "hi")), endTurn("ok"));
        AgentResult r = base("a", model).tool(echo()).build().run("goal");
        boolean resultSent = model.requests().get(1).messages().stream()
                .flatMap(m -> m.content().stream())
                .anyMatch(b -> b instanceof ContentBlock.ToolResult tr
                        && tr.toolUseId().equals("t1") && tr.content().equals("echo:hi"));
        check("tool_use runs the tool and returns a matching tool_result", r.completed() && resultSent);
    }

    private static void sequentialToolCallsCountIterations() {
        ScriptedModelClient model = new ScriptedModelClient(
                toolUse("t1", "echo", Map.of("text", "a")),
                toolUse("t2", "echo", Map.of("text", "b")),
                endTurn("ok"));
        AgentResult r = base("a", model).tool(echo()).build().run("goal");
        long toolResults = r.history().stream().flatMap(m -> m.content().stream())
                .filter(ContentBlock.ToolResult.class::isInstance).count();
        check("iterations equals tool calls plus one", r.iterations() == 3 && toolResults == 2);
    }

    private static void unknownToolBecomesErrorResultAndLoopContinues() {
        ScriptedModelClient model = new ScriptedModelClient(
                toolUse("t1", "nope", Map.of()), endTurn("recovered"));
        AgentResult r = base("a", model).tool(echo()).build().run("goal");
        boolean errored = r.history().stream().flatMap(m -> m.content().stream())
                .anyMatch(b -> b instanceof ContentBlock.ToolResult tr && tr.isError() && tr.content().contains("Unknown tool"));
        check("unknown tool becomes an error result and the loop continues", r.completed() && errored);
    }

    private static void toolFailureBecomesErrorResult() {
        Tool boom = FunctionTool.of(
                new ToolDefinition("boom", "Always fails", InputSchema.object().build()),
                in -> { throw new IllegalStateException("secret internals"); });
        ScriptedModelClient model = new ScriptedModelClient(toolUse("t1", "boom", Map.of()), endTurn("ok"));
        AgentResult r = base("a", model).tool(boom).build().run("goal");
        boolean safe = r.history().stream().flatMap(m -> m.content().stream())
                .anyMatch(b -> b instanceof ContentBlock.ToolResult tr && tr.isError() && !tr.content().contains("secret internals"));
        check("unexpected tool failure becomes an error result without leaking internals", r.completed() && safe);
    }

    private static void iterationCapIsAGuardRailNotAFinish() {
        ScriptedModelClient model = new ScriptedModelClient(
                toolUse("t1", "echo", Map.of("text", "a")),
                toolUse("t2", "echo", Map.of("text", "b")),
                endTurn("never reached"));
        AgentResult r = base("a", model).tool(echo()).maxIterations(2).build().run("goal");
        check("iteration cap stops the run as LIMIT_REACHED", r.status() == RunStatus.LIMIT_REACHED && model.calls() == 2);
    }

    private static void nonEndTurnStopReasonsAreNotFinished() {
        for (StopReason reason : List.of(StopReason.MAX_TOKENS, StopReason.REFUSAL, StopReason.PAUSE_TURN)) {
            AgentResult r = base("a", new ScriptedModelClient(stoppedWith(reason))).build().run("goal");
            check("stop_reason " + reason + " is not treated as a normal finish", r.status() == RunStatus.NOT_FINISHED);
        }
    }

    private static void modelFailureFailsTheRun() {
        Agent agent = Agent.builder("a")
                .instructions(Instructions.forRole("x"))
                .model(request -> { throw new ModelException("overloaded"); })
                .build();
        AgentResult r = agent.run("goal");
        check("a model failure ends the run as FAILED", r.status() == RunStatus.FAILED && r.note().contains("overloaded"));
    }

    // Gates

    private static void gateDenyBlocksTheTool() {
        AtomicInteger executed = new AtomicInteger();
        Tool counting = FunctionTool.of(
                new ToolDefinition("danger", "Does something risky", InputSchema.object().build()),
                in -> { executed.incrementAndGet(); return "ran"; });
        ScriptedModelClient model = new ScriptedModelClient(toolUse("t1", "danger", Map.of()), endTurn("ok"));
        AgentResult r = base("a", model).tool(counting)
                .gate((call, state) -> Decision.deny("not today")).build().run("goal");
        check("a denying gate stops the tool from running", executed.get() == 0 && r.completed());
    }

    private static void prerequisiteGateEnforcesOrder() {
        Tool verify = FunctionTool.of(new ToolDefinition("verify", "Verify", InputSchema.object().build()), in -> "ok");
        AtomicInteger acted = new AtomicInteger();
        Tool act = FunctionTool.of(new ToolDefinition("act", "Act", InputSchema.object().build()),
                in -> { acted.incrementAndGet(); return "done"; });
        ScriptedModelClient model = new ScriptedModelClient(
                toolUse("t1", "act", Map.of()),
                toolUse("t2", "verify", Map.of()),
                toolUse("t3", "act", Map.of()),
                endTurn("ok"));
        base("a", model).tools(verify, act)
                .gate(Gates.prerequisites(Map.of("act", Set.of("verify")))).build().run("goal");
        check("a prerequisite gate blocks the first act and allows it after verify", acted.get() == 1);
    }

    private static void askHumanFailsClosedByDefault() {
        AtomicInteger executed = new AtomicInteger();
        Tool t = FunctionTool.of(new ToolDefinition("pay", "Pay", InputSchema.object().build()),
                in -> { executed.incrementAndGet(); return "paid"; });
        ScriptedModelClient model = new ScriptedModelClient(toolUse("t1", "pay", Map.of()), endTurn("ok"));
        base("a", model).tool(t).gate(Gates.requireApproval(Set.of("pay"), "needs approval")).build().run("goal");
        check("with no approver, an ask-human gate denies (fails closed)", executed.get() == 0);
    }

    private static void askHumanRunsWhenApproved() {
        AtomicInteger executed = new AtomicInteger();
        Tool t = FunctionTool.of(new ToolDefinition("pay", "Pay", InputSchema.object().build()),
                in -> { executed.incrementAndGet(); return "paid"; });
        ScriptedModelClient model = new ScriptedModelClient(toolUse("t1", "pay", Map.of()), endTurn("ok"));
        base("a", model).tool(t).gate(Gates.requireApproval(Set.of("pay"), "needs approval"))
                .approver((call, reason) -> true).build().run("goal");
        check("an approving human lets the tool run", executed.get() == 1);
    }

    private static void gatesCombineFirstNonAllowWins() {
        Gate allow = Gate.allowAll();
        Gate deny = (c, s) -> Decision.deny("first");
        Gate ask = (c, s) -> Decision.askHuman("second");
        var call = new ContentBlock.ToolUse("1", "x", Map.of());
        check("combined gates return the first non-Allow decision",
                allow.and(deny).and(ask).check(call, null) instanceof Decision.Deny
                        && allow.and(ask).and(deny).check(call, null) instanceof Decision.AskHuman);
    }

    private static void resultFilterShrinksOutput() {
        Tool big = FunctionTool.of(new ToolDefinition("big", "Big output", InputSchema.object().build()),
                in -> "x".repeat(1000));
        ScriptedModelClient model = new ScriptedModelClient(toolUse("t1", "big", Map.of()), endTurn("ok"));
        AgentResult r = base("a", model).tool(big)
                .resultFilter((call, out) -> new golden.agent.tool.ToolOutput(out.content().substring(0, 10), out.isError()))
                .build().run("goal");
        boolean trimmed = r.history().stream().flatMap(m -> m.content().stream())
                .anyMatch(b -> b instanceof ContentBlock.ToolResult tr && tr.content().length() == 10);
        check("a result filter trims output before the model sees it", trimmed);
    }

    // State and context

    private static void historyIsSentInFullOnEveryCall() {
        ScriptedModelClient model = new ScriptedModelClient(toolUse("t1", "echo", Map.of("text", "a")), endTurn("ok"));
        base("a", model).tool(echo()).build().run("goal");
        List<ModelRequest> requests = model.requests();
        check("the full history grows and is sent on every call",
                requests.get(0).messages().size() == 1 && requests.get(1).messages().size() == 3);
    }

    private static void eachRunHasIsolatedState() {
        Agent agent = base("a", new ScriptedModelClient(endTurn("one"), endTurn("two"))).build();
        AgentResult first = agent.run("first goal");
        AgentResult second = agent.run("second goal");
        Message m = second.history().get(0);
        check("each run starts with a fresh state",
                first.history().size() == 2 && second.history().size() == 2
                        && ((ContentBlock.Text) m.content().get(0)).text().equals("second goal"));
    }

    // Tools and registry

    private static void typedToolValidatesRequiredArguments() {
        TypedTool<String, String> typed = new TypedTool<>(new ToolDefinition("t", "Typed",
                InputSchema.object().string("name", "Name", true).build())) {
            @Override protected String decode(Map<String, Object> input) throws ToolException { return string(input, "name"); }
            @Override protected String run(String in) { return "hello " + in; }
        };
        boolean rejected = false;
        try {
            typed.execute(Map.of());
        } catch (ToolException e) {
            rejected = e.getMessage().contains("Missing required argument");
        }
        boolean accepted = false;
        try {
            accepted = typed.execute(Map.of("name", "Ada")).content().equals("hello Ada");
        } catch (ToolException ignored) { }
        check("a typed tool rejects missing arguments and decodes valid ones", rejected && accepted);
    }

    private static void registrySubsetGivesLeastPrivilege() {
        ToolRegistry all = ToolRegistry.of(echo());
        ToolRegistry none = all.subset(Set.of());
        boolean duplicate = false;
        try {
            all.register(echo());
        } catch (IllegalArgumentException e) {
            duplicate = true;
        }
        check("a registry subset restricts tools and duplicates are rejected",
                none.names().isEmpty() && all.names().contains("echo") && duplicate);
    }

    // Multi-agent

    private static void subAgentReceivesOnlyExplicitContext() {
        ScriptedModelClient subModel = new ScriptedModelClient(endTurn("sub result"));
        Agent sub = base("worker", subModel).description("Does work").build();
        ScriptedModelClient coordModel = new ScriptedModelClient(
                toolUse("c1", "delegate_to_worker", Map.of("task", "do it", "context", "prior finding X")),
                endTurn("final"));
        AgentResult r = base("coord", coordModel).subAgent(sub).build().run("secret coordinator goal");

        String subSaw = subModel.requests().get(0).messages().get(0).content().get(0) instanceof ContentBlock.Text t ? t.text() : "";
        check("a sub agent sees only the task and context it was given",
                r.completed() && subSaw.contains("do it") && subSaw.contains("prior finding X")
                        && !subSaw.contains("secret coordinator goal"));
    }

    private static void subAgentFailureIsAStructuredError() {
        Agent sub = base("worker", new ScriptedModelClient(stoppedWith(StopReason.MAX_TOKENS))).build();
        ScriptedModelClient coordModel = new ScriptedModelClient(
                toolUse("c1", "delegate_to_worker", Map.of("task", "do it")), endTurn("continued with a gap"));
        AgentResult r = base("coord", coordModel).subAgent(sub).build().run("goal");
        boolean structured = r.history().stream().flatMap(m -> m.content().stream())
                .anyMatch(b -> b instanceof ContentBlock.ToolResult tr && tr.isError()
                        && tr.content().contains("SUB_AGENT_FAILURE") && tr.content().contains("status=NOT_FINISHED"));
        check("a failed sub agent returns a structured error and the coordinator continues", r.completed() && structured);
    }

    // Provider and text utilities

    @SuppressWarnings("unchecked")
    private static void jsonRoundTrip() {
        String text = "{\"a\":1,\"b\":[true,null,\"x\\ny\"],\"c\":{\"d\":2.5}}";
        Object parsed = Json.parse(text);
        String written = Json.write(parsed);
        Object again = Json.parse(written);
        Map<String, Object> m = (Map<String, Object>) again;
        check("JSON parses, writes and round-trips",
                parsed.equals(again) && ((Long) m.get("a")) == 1L && written.contains("\\n"));
    }

    private static void anthropicMappingMatchesTheApiShape() throws Exception {
        Class<?> c = Class.forName("golden.agent.provider.AnthropicModelClient");
        Method build = c.getDeclaredMethod("buildBody", String.class, ModelRequest.class);
        Method parse = c.getDeclaredMethod("parseResponse", String.class);
        build.setAccessible(true);
        parse.setAccessible(true);

        ModelRequest request = new ModelRequest("sys",
                List.of(Message.user("hi"),
                        Message.assistant(List.of(new ContentBlock.ToolUse("t1", "echo", Map.of("text", "a")))),
                        Message.toolResults(List.of(new ContentBlock.ToolResult("t1", "echo:a", false)))),
                List.of(echo().definition()), 256);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) Json.parse((String) build.invoke(null, "some-model", request));
        boolean bodyOk = "some-model".equals(body.get("model"))
                && ((Long) body.get("max_tokens")) == 256L
                && "sys".equals(body.get("system"))
                && ((List<?>) body.get("messages")).size() == 3
                && ((List<?>) body.get("tools")).size() == 1;

        String reply = "{\"content\":[{\"type\":\"text\",\"text\":\"hello\"},"
                + "{\"type\":\"tool_use\",\"id\":\"t9\",\"name\":\"echo\",\"input\":{\"text\":\"z\"}}],"
                + "\"stop_reason\":\"tool_use\"}";
        golden.agent.model.ModelResponse response = (golden.agent.model.ModelResponse) parse.invoke(null, reply);
        boolean parsedOk = response.stopReason() == StopReason.TOOL_USE
                && response.toolUses().size() == 1 && response.text().equals("hello");

        boolean unknownRejected = false;
        try {
            parse.invoke(null, "{\"content\":[],\"stop_reason\":\"surprise\"}");
        } catch (java.lang.reflect.InvocationTargetException e) {
            unknownRejected = e.getCause() instanceof ModelException;
        }
        check("the Anthropic request and response mapping matches the API shape",
                bodyOk && parsedOk && unknownRejected);
    }

    private static void instructionsComposeAndRender() {
        Instructions shared = Instructions.forRole("Be careful.").withRule("Never guess.");
        Instructions specific = Instructions.forRole("You research.").withRule("Cite sources.").withOutputFormat("Bullets");
        String text = shared.and(specific).render();
        check("instructions compose and render role, rules and format",
                text.contains("Never guess.") && text.contains("Cite sources.") && text.contains("Bullets"));
    }
}
