package golden.agent.model;

/** A model call failed (network, rate limit, overload). The loop turns it into a FAILED run. */
public class ModelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelException(String message) {
        super(message);
    }

    public ModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
