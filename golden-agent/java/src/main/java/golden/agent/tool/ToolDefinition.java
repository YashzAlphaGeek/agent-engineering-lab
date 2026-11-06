package golden.agent.tool;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What the model sees about a tool: a name, a description and an input schema.
 * The description is the only way the model knows when to use the tool, so write it carefully:
 * what it does, when to use it, when not to, and what it returns.
 */
public record ToolDefinition(String name, String description, InputSchema inputSchema) {

    private static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    public ToolDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(inputSchema, "inputSchema");
        if (!VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Tool name must match [a-zA-Z0-9_-]{1,64}: " + name);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Tool '" + name + "' needs a description");
        }
    }
}
