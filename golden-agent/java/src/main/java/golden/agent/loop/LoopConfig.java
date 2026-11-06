package golden.agent.loop;

/** Limits for one run. The iteration cap is a guard rail against runaway loops, never the normal way to stop. */
public record LoopConfig(int maxIterations, int maxTokens) {

    public LoopConfig {
        if (maxIterations <= 0 || maxTokens <= 0) {
            throw new IllegalArgumentException("maxIterations and maxTokens must be positive");
        }
    }

    public static LoopConfig defaults() {
        return new LoopConfig(20, 1024);
    }

    public LoopConfig withMaxIterations(int value) {
        return new LoopConfig(value, maxTokens);
    }

    public LoopConfig withMaxTokens(int value) {
        return new LoopConfig(maxIterations, value);
    }
}
