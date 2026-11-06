package golden.agent.state;

/** Where a run stands. Only {@link #COMPLETED} means the model finished normally. */
public enum RunStatus {
    /** The loop is still going. */
    RUNNING,
    /** The model ended its turn normally ({@code end_turn}). */
    COMPLETED,
    /** The iteration cap stopped the run. This is a guard rail, not a normal finish. */
    LIMIT_REACHED,
    /** The model stopped for another reason, such as {@code max_tokens} or a refusal. */
    NOT_FINISHED,
    /** A model call failed or the response was malformed. */
    FAILED
}
