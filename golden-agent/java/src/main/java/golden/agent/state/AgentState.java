package golden.agent.state;

import golden.agent.model.Message;
import golden.agent.model.StopReason;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Element 5, the state of one run.
 *
 * <p>Holds the ordered message history (the agent's memory, sent on every model call), the goal,
 * the iteration count and the outcome. A new instance is created for every run, and instances are
 * never shared between agents or runs, which keeps each agent's context isolated.
 */
public final class AgentState {

    private final String runId = UUID.randomUUID().toString();
    private final String goal;
    private final List<Message> history = new ArrayList<>();
    private final Set<String> succeededTools = new LinkedHashSet<>();

    private int iterations;
    private RunStatus status = RunStatus.RUNNING;
    private StopReason lastStopReason;
    private String finalText = "";
    private String note = "";

    private AgentState(String goal) {
        this.goal = goal;
    }

    /** Start a run. The goal becomes the first user message. */
    public static AgentState start(String goal) {
        AgentState state = new AgentState(goal);
        state.history.add(Message.user(goal));
        return state;
    }

    public String runId() { return runId; }
    public String goal() { return goal; }
    public int iterations() { return iterations; }
    public RunStatus status() { return status; }
    public StopReason lastStopReason() { return lastStopReason; }
    public String finalText() { return finalText; }
    public String note() { return note; }

    public List<Message> history() {
        return Collections.unmodifiableList(history);
    }

    public void append(Message message) {
        history.add(message);
    }

    public void incrementIteration() {
        iterations++;
    }

    public void recordStopReason(StopReason reason) {
        this.lastStopReason = reason;
    }

    /** Remember that a tool ran successfully. Gates use this to enforce prerequisites. */
    public void markToolSucceeded(String toolName) {
        succeededTools.add(toolName);
    }

    public boolean hasSucceeded(String toolName) {
        return succeededTools.contains(toolName);
    }

    public void complete(String text) {
        this.status = RunStatus.COMPLETED;
        this.finalText = text;
    }

    public void stop(RunStatus reason, String explanation) {
        if (reason == RunStatus.RUNNING || reason == RunStatus.COMPLETED) {
            throw new IllegalArgumentException("Use complete() for a normal finish");
        }
        this.status = reason;
        this.note = explanation;
    }
}
