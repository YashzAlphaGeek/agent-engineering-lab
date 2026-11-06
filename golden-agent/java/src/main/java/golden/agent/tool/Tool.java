package golden.agent.tool;

import java.util.Map;

/**
 * Element 3, a tool. A described capability the model may ask for.
 *
 * <p>The interface is deliberately small: a definition and an execute method. Behind it can be an API call,
 * a database query, a local function, an MCP server, or another agent (see {@code DelegationTool}).
 */
public interface Tool {

    ToolDefinition definition();

    ToolOutput execute(Map<String, Object> input) throws ToolException;

    default String name() {
        return definition().name();
    }
}
