package golden.agent.guard;

import golden.agent.model.ContentBlock;

/**
 * Asked when a gate returns {@link Decision.AskHuman}. Plug in a console prompt, a ticket, a chat message
 * or a web form. The default fails closed: nothing is approved unless a person says so.
 */
@FunctionalInterface
public interface HumanApprover {

    boolean approve(ContentBlock.ToolUse call, String reason);

    static HumanApprover denyAll() {
        return (call, reason) -> false;
    }
}
