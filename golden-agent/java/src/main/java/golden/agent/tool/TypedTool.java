package golden.agent.tool;

import java.util.Map;

/**
 * A tool with a typed input {@code I} and a typed output {@code O}.
 *
 * <p>The template method {@link #execute} validates required arguments, decodes the raw map into {@code I},
 * runs the typed logic, and encodes {@code O} as text for the model. Subclasses write only
 * {@link #decode}, {@link #run} and optionally {@link #encode}.
 */
public abstract class TypedTool<I, O> implements Tool {

    private final ToolDefinition definition;

    protected TypedTool(ToolDefinition definition) {
        this.definition = definition;
    }

    @Override
    public final ToolDefinition definition() {
        return definition;
    }

    @Override
    public final ToolOutput execute(Map<String, Object> input) throws ToolException {
        for (String key : definition.inputSchema().required()) {
            if (input.get(key) == null) {
                throw new ToolException("Missing required argument '" + key + "'");
            }
        }
        I typed = decode(input);
        O result = run(typed);
        return ToolOutput.ok(encode(result));
    }

    /** Turn the raw argument map into the typed input. */
    protected abstract I decode(Map<String, Object> input) throws ToolException;

    /** Do the work. */
    protected abstract O run(I input) throws ToolException;

    /** Turn the typed output into the text returned to the model. */
    protected String encode(O output) {
        return String.valueOf(output);
    }

    // Decoding helpers

    protected static String string(Map<String, Object> input, String key) throws ToolException {
        Object v = input.get(key);
        if (!(v instanceof String s)) {
            throw new ToolException("Argument '" + key + "' must be a string");
        }
        return s;
    }

    protected static int integer(Map<String, Object> input, String key) throws ToolException {
        Object v = input.get(key);
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return n.intValue();
        }
        throw new ToolException("Argument '" + key + "' must be an integer");
    }
}
