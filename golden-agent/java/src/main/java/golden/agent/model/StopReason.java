package golden.agent.model;

/**
 * Why the model stopped. This is the only reliable signal for controlling the loop.
 * Anything other than {@link #END_TURN} or {@link #TOOL_USE} means "not finished, find out why".
 */
public enum StopReason {
    END_TURN,
    TOOL_USE,
    MAX_TOKENS,
    STOP_SEQUENCE,
    PAUSE_TURN,
    REFUSAL,
    CONTEXT_WINDOW_EXCEEDED
}
