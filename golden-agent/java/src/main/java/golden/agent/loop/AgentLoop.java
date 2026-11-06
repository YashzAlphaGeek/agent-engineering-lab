package golden.agent.loop;

import golden.agent.guard.Decision;
import golden.agent.guard.Gate;
import golden.agent.guard.HumanApprover;
import golden.agent.guard.ResultFilter;
import golden.agent.instructions.Instructions;
import golden.agent.model.ContentBlock;
import golden.agent.model.Message;
import golden.agent.model.ModelClient;
import golden.agent.model.ModelException;
import golden.agent.model.ModelRequest;
import golden.agent.model.ModelResponse;
import golden.agent.state.AgentState;
import golden.agent.state.RunStatus;
import golden.agent.tool.Tool;
import golden.agent.tool.ToolException;
import golden.agent.tool.ToolOutput;
import golden.agent.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Element 4, the loop. The application code around the model.
 *
 * <p>Each pass: send the full history and tools, read {@code stop_reason}, and either finish
 * ({@code end_turn}), run the requested tools and loop ({@code tool_use}), or stop because the
 * model ended for another reason. Every tool call passes the gate first, and failures become error
 * results the model can read.
 *
 * <p>The loop depends only on abstractions ({@link ModelClient}, {@link ToolRegistry}, {@link Gate}),
 * so any provider or tool set can be plugged in. It is stateless between runs.
 */
public final class AgentLoop {

    private final ModelClient model;
    private final Instructions instructions;
    private final ToolRegistry tools;
    private final Gate gate;
    private final HumanApprover approver;
    private final ResultFilter filter;
    private final LoopConfig config;

    public AgentLoop(
            ModelClient model,
            Instructions instructions,
            ToolRegistry tools,
            Gate gate,
            HumanApprover approver,
            ResultFilter filter,
            LoopConfig config) {
        this.model = model;
        this.instructions = instructions;
        this.tools = tools;
        this.gate = gate;
        this.approver = approver;
        this.filter = filter;
        this.config = config;
    }

    /** Run to completion and return the outcome. Each call uses a fresh, isolated state. */
    public AgentResult run(String goal) {
        AgentState state = AgentState.start(goal);
        String system = instructions.render();

        while (state.status() == RunStatus.RUNNING) {
            if (state.iterations() >= config.maxIterations()) {
                state.stop(RunStatus.LIMIT_REACHED, "Iteration cap of " + config.maxIterations() + " reached");
                break;
            }
            state.incrementIteration();

            ModelResponse response;
            try {
                response = model.send(new ModelRequest(system, state.history(), tools.definitions(), config.maxTokens()));
            } catch (ModelException e) {
                state.stop(RunStatus.FAILED, "Model call failed: " + e.getMessage());
                break;
            }

            state.recordStopReason(response.stopReason());
            state.append(Message.assistant(response.content()));

            switch (response.stopReason()) {
                case END_TURN -> state.complete(response.text());
                case TOOL_USE -> handleToolUse(response, state);
                default -> state.stop(RunStatus.NOT_FINISHED,
                        "Model stopped with " + response.stopReason() + ", which is not a normal finish");
            }
        }
        return AgentResult.from(state);
    }

    private void handleToolUse(ModelResponse response, AgentState state) {
        List<ContentBlock.ToolUse> requests = response.toolUses();
        if (requests.isEmpty()) {
            state.stop(RunStatus.FAILED, "stop_reason was tool_use but the response had no tool_use block");
            return;
        }
        List<ContentBlock.ToolResult> results = new ArrayList<>();
        for (ContentBlock.ToolUse request : requests) {
            results.add(runOne(request, state));
        }
        // All results for one assistant turn go back together in a single user message.
        state.append(Message.toolResults(results));
    }

    private ContentBlock.ToolResult runOne(ContentBlock.ToolUse request, AgentState state) {
        Decision decision = gate.check(request, state);

        ToolOutput output = switch (decision) {
            case Decision.Allow a -> execute(request, state);
            case Decision.Deny d -> ToolOutput.error("Blocked: " + d.reason());
            case Decision.AskHuman h -> approver.approve(request, h.reason())
                    ? execute(request, state)
                    : ToolOutput.error("Not approved by a human: " + h.reason());
        };

        ToolOutput visible = filter.filter(request, output);
        return new ContentBlock.ToolResult(request.id(), visible.content(), visible.isError());
    }

    private ToolOutput execute(ContentBlock.ToolUse request, AgentState state) {
        Optional<Tool> tool = tools.find(request.name());
        if (tool.isEmpty()) {
            return ToolOutput.error("Unknown tool '" + request.name() + "'. Available: " + tools.names());
        }
        try {
            ToolOutput output = tool.get().execute(request.input());
            if (!output.isError()) {
                state.markToolSucceeded(request.name());
            }
            return output;
        } catch (ToolException e) {
            return ToolOutput.error(e.getMessage());
        } catch (RuntimeException e) {
            return ToolOutput.error("Tool '" + request.name() + "' failed unexpectedly: " + e.getClass().getSimpleName());
        }
    }
}
