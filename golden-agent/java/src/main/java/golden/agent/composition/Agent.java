package golden.agent.composition;

import golden.agent.guard.Gate;
import golden.agent.guard.HumanApprover;
import golden.agent.guard.ResultFilter;
import golden.agent.instructions.Instructions;
import golden.agent.loop.AgentLoop;
import golden.agent.loop.AgentResult;
import golden.agent.loop.LoopConfig;
import golden.agent.model.ModelClient;
import golden.agent.tool.Tool;
import golden.agent.tool.ToolRegistry;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The public entry point of the library: one agent made of the five core elements.
 *
 * <pre>{@code
 * Agent agent = Agent.builder("support")
 *         .model(modelClient)                      // element 1
 *         .instructions(Instructions.forRole("You are a support agent.")) // element 2
 *         .tool(lookupTool)                        // element 3
 *         .maxIterations(10)                       // element 4 (loop limits)
 *         .gate(Gates.prerequisites(rules))        // guardrail
 *         .build();
 * AgentResult result = agent.run("Refund order 123");   // element 5, a fresh state per run
 * }</pre>
 *
 * <p>An {@code Agent} is immutable and holds no run state, so one instance can be reused and run many
 * times, even concurrently. Every {@link #run} creates its own isolated state.
 */
public final class Agent {

    private static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,50}");

    private final String name;
    private final String description;
    private final ModelClient model;
    private final Instructions instructions;
    private final ToolRegistry tools;
    private final Gate gate;
    private final HumanApprover approver;
    private final ResultFilter filter;
    private final LoopConfig config;

    private Agent(Builder b) {
        this.name = b.name;
        this.description = b.description;
        this.model = b.model;
        this.instructions = b.instructions;
        this.tools = b.tools;
        this.gate = b.gate;
        this.approver = b.approver;
        this.filter = b.filter;
        this.config = b.config;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() { return name; }
    public String description() { return description; }
    public ToolRegistry tools() { return tools; }

    /** Run the agent on a goal. Safe to call repeatedly and concurrently. */
    public AgentResult run(String goal) {
        return new AgentLoop(model, instructions, tools, gate, approver, filter, config).run(goal);
    }

    /** Expose this agent to another agent as a delegation tool (the sub agent pattern). */
    public Tool asTool() {
        return new DelegationTool(this);
    }

    public static final class Builder {
        private final String name;
        private String description;
        private ModelClient model;
        private Instructions instructions;
        private final ToolRegistry tools = new ToolRegistry();
        private Gate gate = Gate.allowAll();
        private HumanApprover approver = HumanApprover.denyAll();
        private ResultFilter filter = ResultFilter.identity();
        private LoopConfig config = LoopConfig.defaults();

        private Builder(String name) {
            if (name == null || !VALID_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Agent name must match [a-zA-Z0-9_-]{1,50}: " + name);
            }
            this.name = name;
            this.description = "Delegates a task to the " + name + " agent";
        }

        /** What this agent is for. When used as a sub agent this is what the coordinator reads. */
        public Builder description(String description) {
            this.description = Objects.requireNonNull(description);
            return this;
        }

        public Builder model(ModelClient model) {
            this.model = model;
            return this;
        }

        public Builder instructions(Instructions instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder tool(Tool tool) {
            tools.register(tool);
            return this;
        }

        public Builder tools(Tool... more) {
            for (Tool tool : more) {
                tools.register(tool);
            }
            return this;
        }

        /** Add every tool from a registry, for example a restricted {@code subset}. */
        public Builder tools(ToolRegistry registry) {
            registry.all().forEach(tools::register);
            return this;
        }

        /** Give this agent a sub agent, exposed as a delegation tool. */
        public Builder subAgent(Agent sub) {
            return tool(sub.asTool());
        }

        /** Add a gate. Several gates combine, and the first non-Allow decision wins. */
        public Builder gate(Gate gate) {
            this.gate = this.gate.and(gate);
            return this;
        }

        public Builder approver(HumanApprover approver) {
            this.approver = approver;
            return this;
        }

        public Builder resultFilter(ResultFilter filter) {
            this.filter = filter;
            return this;
        }

        public Builder config(LoopConfig config) {
            this.config = config;
            return this;
        }

        public Builder maxIterations(int max) {
            this.config = config.withMaxIterations(max);
            return this;
        }

        public Agent build() {
            Objects.requireNonNull(model, "model is required");
            Objects.requireNonNull(instructions, "instructions are required");
            return new Agent(this);
        }
    }
}
