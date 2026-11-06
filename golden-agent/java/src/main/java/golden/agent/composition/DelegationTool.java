package golden.agent.composition;

import golden.agent.loop.AgentResult;
import golden.agent.tool.InputSchema;
import golden.agent.tool.Tool;
import golden.agent.tool.ToolDefinition;
import golden.agent.tool.ToolException;
import golden.agent.tool.ToolOutput;

import java.util.Map;

/**
 * A tool whose implementation is another agent. This is how a coordinator delegates to a sub agent.
 *
 * <p>The sub agent never sees the coordinator's conversation. It receives only the {@code task} and the
 * explicit {@code context} that the coordinator passes. When the sub agent does not finish, the tool returns a
 * structured error (what was attempted, status, partial text) instead of an empty success, so the coordinator
 * can retry, route elsewhere, or continue with partial results.
 */
public final class DelegationTool implements Tool {

    private final Agent agent;
    private final ToolDefinition definition;

    DelegationTool(Agent agent) {
        this.agent = agent;
        this.definition = new ToolDefinition(
                "delegate_to_" + agent.name(),
                agent.description(),
                InputSchema.object()
                        .string("task", "The sub-task to perform, stated completely", true)
                        .string("context", "Everything the sub agent needs, such as goal and prior findings", false)
                        .build());
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolOutput execute(Map<String, Object> input) throws ToolException {
        Object task = input.get("task");
        if (!(task instanceof String taskText) || taskText.isBlank()) {
            throw new ToolException("Missing required argument 'task'");
        }
        String context = input.get("context") instanceof String c ? c : "";
        String goal = context.isBlank() ? taskText : taskText + "\n\nContext:\n" + context;

        AgentResult result = agent.run(goal);
        if (result.completed()) {
            return ToolOutput.ok(result.text());
        }
        return ToolOutput.error(
                "SUB_AGENT_FAILURE agent=" + agent.name()
                        + " status=" + result.status()
                        + " iterations=" + result.iterations()
                        + " note=\"" + result.note() + "\""
                        + " task=\"" + taskText + "\""
                        + " partial=\"" + result.text() + "\"");
    }
}
