package golden.agent.model;

import golden.agent.tool.ToolDefinition;

import java.util.List;

/** Everything sent to the model on one call. The API is stateless, so history is always included. */
public record ModelRequest(String system, List<Message> messages, List<ToolDefinition> tools, int maxTokens) {

    public ModelRequest {
        system = system == null ? "" : system;
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
    }
}
