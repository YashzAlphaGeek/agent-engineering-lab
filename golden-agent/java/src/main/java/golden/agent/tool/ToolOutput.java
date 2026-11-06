package golden.agent.tool;

/** The result of running a tool. A failure is data the model can read and react to, not a crash. */
public record ToolOutput(String content, boolean isError) {

    public static ToolOutput ok(String content) {
        return new ToolOutput(content, false);
    }

    public static ToolOutput error(String message) {
        return new ToolOutput(message, true);
    }
}
