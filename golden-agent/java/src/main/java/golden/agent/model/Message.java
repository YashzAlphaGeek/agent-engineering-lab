package golden.agent.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One turn in the conversation. The full list of messages is the agent's memory. */
public record Message(Role role, List<ContentBlock> content) {

    public Message {
        Objects.requireNonNull(role, "role");
        content = List.copyOf(content);
    }

    public static Message user(String text) {
        return new Message(Role.USER, List.of(new ContentBlock.Text(text)));
    }

    public static Message assistant(List<ContentBlock> blocks) {
        return new Message(Role.ASSISTANT, blocks);
    }

    /** Tool results travel back to the model inside a user message. */
    public static Message toolResults(List<ContentBlock.ToolResult> results) {
        return new Message(Role.USER, new ArrayList<ContentBlock>(results));
    }
}
