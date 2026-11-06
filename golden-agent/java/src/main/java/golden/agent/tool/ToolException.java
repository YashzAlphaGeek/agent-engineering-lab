package golden.agent.tool;

/** An expected tool failure, such as bad arguments or a missing record. Its message goes back to the model. */
public class ToolException extends Exception {

    private static final long serialVersionUID = 1L;

    public ToolException(String message) {
        super(message);
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
