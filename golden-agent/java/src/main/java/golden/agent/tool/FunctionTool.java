package golden.agent.tool;

import java.util.Map;
import java.util.function.Function;

/** The quickest way to make a tool: a definition plus a function. */
public final class FunctionTool implements Tool {

    private final ToolDefinition definition;
    private final Function<Map<String, Object>, String> body;

    private FunctionTool(ToolDefinition definition, Function<Map<String, Object>, String> body) {
        this.definition = definition;
        this.body = body;
    }

    public static Tool of(ToolDefinition definition, Function<Map<String, Object>, String> body) {
        return new FunctionTool(definition, body);
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolOutput execute(Map<String, Object> input) throws ToolException {
        for (String key : definition.inputSchema().required()) {
            if (input.get(key) == null) {
                throw new ToolException("Missing required argument '" + key + "'");
            }
        }
        return ToolOutput.ok(body.apply(input));
    }
}
