package golden.agent.guard;

import golden.agent.model.ContentBlock;
import golden.agent.state.AgentState;

/**
 * The VETO. A programmatic check that runs before every tool call.
 *
 * <p>Because it is ordinary code outside the model, the model cannot skip or argue with it.
 * Gates compose with {@link #and}: the first decision that is not Allow wins.
 */
@FunctionalInterface
public interface Gate {

    Decision check(ContentBlock.ToolUse call, AgentState state);

    default Gate and(Gate next) {
        return (call, state) -> {
            Decision first = check(call, state);
            return first instanceof Decision.Allow ? next.check(call, state) : first;
        };
    }

    static Gate allowAll() {
        return (call, state) -> Decision.allow();
    }
}
