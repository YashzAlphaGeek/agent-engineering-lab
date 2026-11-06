package golden.agent.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** What comes back from the model: content blocks plus the reason it stopped. */
public record ModelResponse(List<ContentBlock> content, StopReason stopReason) {

    public ModelResponse {
        content = List.copyOf(content);
        Objects.requireNonNull(stopReason, "stopReason");
    }

    /** All text blocks joined together. */
    public String text() {
        return content.stream()
                .filter(ContentBlock.Text.class::isInstance)
                .map(b -> ((ContentBlock.Text) b).text())
                .collect(Collectors.joining("\n"));
    }

    /** All tool requests in this response. A response may contain several. */
    public List<ContentBlock.ToolUse> toolUses() {
        return content.stream()
                .filter(ContentBlock.ToolUse.class::isInstance)
                .map(ContentBlock.ToolUse.class::cast)
                .toList();
    }
}
