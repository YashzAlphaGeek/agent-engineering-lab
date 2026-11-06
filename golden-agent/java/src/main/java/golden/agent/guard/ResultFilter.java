package golden.agent.guard;

import golden.agent.model.ContentBlock;
import golden.agent.tool.ToolOutput;

/**
 * Runs after a tool and before the model sees the result. Use it to trim large output,
 * normalise formats or redact sensitive fields. Shrinking what a tool returns is the right way
 * to save tokens, rather than dropping results from the history.
 */
@FunctionalInterface
public interface ResultFilter {

    ToolOutput filter(ContentBlock.ToolUse call, ToolOutput output);

    static ResultFilter identity() {
        return (call, output) -> output;
    }
}
