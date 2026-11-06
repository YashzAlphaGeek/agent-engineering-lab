package golden.agent.loop;

import golden.agent.model.Message;
import golden.agent.model.StopReason;
import golden.agent.state.AgentState;
import golden.agent.state.RunStatus;

import java.util.List;

/** The outcome of one run, copied out of the state so callers cannot change it. */
public record AgentResult(
        RunStatus status,
        String text,
        String note,
        int iterations,
        StopReason lastStopReason,
        List<Message> history) {

    public AgentResult {
        history = List.copyOf(history);
    }

    public boolean completed() {
        return status == RunStatus.COMPLETED;
    }

    static AgentResult from(AgentState state) {
        return new AgentResult(
                state.status(), state.finalText(), state.note(),
                state.iterations(), state.lastStopReason(), state.history());
    }
}
