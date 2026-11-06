package golden.agent.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * A deterministic test double for {@link ModelClient}. It replays a fixed script of responses.
 *
 * <p>Running past the end of the script throws, so a loop that asks the model more often
 * than expected is caught instead of being hidden.
 */
public final class ScriptedModelClient implements ModelClient {

    private final Deque<ModelResponse> script;
    private final List<ModelRequest> requests = new ArrayList<>();

    public ScriptedModelClient(ModelResponse... responses) {
        this.script = new ArrayDeque<>(List.of(responses));
    }

    @Override
    public ModelResponse send(ModelRequest request) {
        requests.add(request);
        ModelResponse next = script.poll();
        if (next == null) {
            throw new IllegalStateException("Script exhausted after " + requests.size() + " calls");
        }
        return next;
    }

    /** Every request received so far, for assertions. */
    public List<ModelRequest> requests() {
        return List.copyOf(requests);
    }

    public int calls() {
        return requests.size();
    }

    // Script building helpers

    public static ModelResponse endTurn(String text) {
        return new ModelResponse(List.of(new ContentBlock.Text(text)), StopReason.END_TURN);
    }

    public static ModelResponse toolUse(String id, String toolName, Map<String, Object> input) {
        return new ModelResponse(
                List.of(new ContentBlock.ToolUse(id, toolName, input)), StopReason.TOOL_USE);
    }

    public static ModelResponse stoppedWith(StopReason reason) {
        return new ModelResponse(List.of(new ContentBlock.Text("(stopped)")), reason);
    }
}
