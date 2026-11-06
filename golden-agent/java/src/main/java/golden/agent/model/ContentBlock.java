package golden.agent.model;

import java.util.Map;

/**
 * One piece of a message. The three shapes mirror the Messages API:
 * plain text, a tool request from the model, and the result of that request.
 *
 * <p>The interface is sealed, so a {@code switch} over it is checked for
 * completeness by the compiler.
 */
public sealed interface ContentBlock {

    /** Plain text from the user or the model. */
    record Text(String text) implements ContentBlock { }

    /** The model asks the application to run a tool. It is a request, never an execution. */
    record ToolUse(String id, String name, Map<String, Object> input) implements ContentBlock {
        public ToolUse {
            input = input == null ? Map.of() : Map.copyOf(input);
        }
    }

    /** The application's answer to a {@link ToolUse}, matched by {@code toolUseId}. */
    record ToolResult(String toolUseId, String content, boolean isError) implements ContentBlock { }
}
