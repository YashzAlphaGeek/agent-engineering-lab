package golden.agent.provider;

import golden.agent.model.ContentBlock;
import golden.agent.model.Message;
import golden.agent.model.ModelClient;
import golden.agent.model.ModelException;
import golden.agent.model.ModelRequest;
import golden.agent.model.ModelResponse;
import golden.agent.model.Role;
import golden.agent.model.StopReason;
import golden.agent.tool.ToolDefinition;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ModelClient} for the Anthropic Messages API, written with only the JDK's {@code java.net.http}.
 *
 * <p>It is a reference adapter. The request and response mapping is covered by offline tests, but it has not been
 * run against the live API in this repository, so verify it with your own key before depending on it.
 * Rate limits and server errors are retried with exponential backoff, and an unknown {@code stop_reason}
 * is rejected instead of being treated as a normal finish.
 *
 * <p>The model id is always supplied by the caller. Keep keys in the environment or a secret store.
 */
public final class AnthropicModelClient implements ModelClient {

    private static final String DEFAULT_ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxRetries;

    private AnthropicModelClient(Builder b) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.endpoint = URI.create(b.endpoint);
        this.apiKey = b.apiKey;
        this.model = b.model;
        this.timeout = b.timeout;
        this.maxRetries = b.maxRetries;
    }

    public static Builder builder(String apiKey, String model) {
        return new Builder(apiKey, model);
    }

    /** Read the key from the {@code ANTHROPIC_API_KEY} environment variable. */
    public static AnthropicModelClient fromEnvironment(String model) {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set");
        }
        return builder(key, model).build();
    }

    @Override
    public ModelResponse send(ModelRequest request) throws ModelException {
        String body = buildBody(model, request);
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        ModelException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                sleep(500L << (attempt - 1));
            }
            try {
                HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    return parseResponse(response.body());
                }
                String detail = "HTTP " + status + ": " + abbreviate(response.body());
                if (status == 429 || status >= 500) {
                    last = new ModelException(detail);
                    continue;
                }
                throw new ModelException(detail);
            } catch (IOException e) {
                last = new ModelException("Network error: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ModelException("Interrupted while calling the model", e);
            }
        }
        throw last;
    }

    // Mapping, kept package-visible so it can be tested without a network

    static String buildBody(String model, ModelRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", request.maxTokens());
        if (!request.system().isBlank()) {
            body.put("system", request.system());
        }
        List<Object> messages = new ArrayList<>();
        for (Message message : request.messages()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", message.role() == Role.USER ? "user" : "assistant");
            List<Object> blocks = new ArrayList<>();
            message.content().forEach(block -> blocks.add(toJson(block)));
            m.put("content", blocks);
            messages.add(m);
        }
        body.put("messages", messages);
        if (!request.tools().isEmpty()) {
            List<Object> tools = new ArrayList<>();
            for (ToolDefinition t : request.tools()) {
                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put("name", t.name());
                tool.put("description", t.description());
                tool.put("input_schema", t.inputSchema().toJsonSchema());
                tools.add(tool);
            }
            body.put("tools", tools);
        }
        return Json.write(body);
    }

    private static Map<String, Object> toJson(ContentBlock block) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (block) {
            case ContentBlock.Text t -> {
                m.put("type", "text");
                m.put("text", t.text());
            }
            case ContentBlock.ToolUse u -> {
                m.put("type", "tool_use");
                m.put("id", u.id());
                m.put("name", u.name());
                m.put("input", u.input());
            }
            case ContentBlock.ToolResult r -> {
                m.put("type", "tool_result");
                m.put("tool_use_id", r.toolUseId());
                m.put("content", r.content());
                if (r.isError()) {
                    m.put("is_error", true);
                }
            }
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    static ModelResponse parseResponse(String json) {
        Object parsed = Json.parse(json);
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new ModelException("Response was not a JSON object");
        }
        Object rawContent = root.get("content");
        if (!(rawContent instanceof List<?> list)) {
            throw new ModelException("Response has no content array");
        }
        List<ContentBlock> blocks = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> block)) {
                continue;
            }
            String type = String.valueOf(block.get("type"));
            switch (type) {
                case "text" -> blocks.add(new ContentBlock.Text(String.valueOf(block.get("text"))));
                case "tool_use" -> {
                    Object input = block.get("input");
                    blocks.add(new ContentBlock.ToolUse(
                            String.valueOf(block.get("id")),
                            String.valueOf(block.get("name")),
                            input instanceof Map<?, ?> ? (Map<String, Object>) input : Map.of()));
                }
                default -> { /* other block types are not used by this library */ }
            }
        }
        return new ModelResponse(blocks, stopReason(String.valueOf(root.get("stop_reason"))));
    }

    static StopReason stopReason(String value) {
        return switch (value) {
            case "end_turn" -> StopReason.END_TURN;
            case "tool_use" -> StopReason.TOOL_USE;
            case "max_tokens" -> StopReason.MAX_TOKENS;
            case "stop_sequence" -> StopReason.STOP_SEQUENCE;
            case "pause_turn" -> StopReason.PAUSE_TURN;
            case "refusal" -> StopReason.REFUSAL;
            case "model_context_window_exceeded" -> StopReason.CONTEXT_WINDOW_EXCEEDED;
            default -> throw new ModelException("Unknown stop_reason '" + value + "'");
        };
    }

    private static String abbreviate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelException("Interrupted while waiting to retry", e);
        }
    }

    public static final class Builder {
        private final String apiKey;
        private final String model;
        private String endpoint = DEFAULT_ENDPOINT;
        private Duration timeout = Duration.ofSeconds(60);
        private int maxRetries = 2;

        private Builder(String apiKey, String model) {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("apiKey is required");
            }
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("model is required");
            }
            this.apiKey = apiKey;
            this.model = model;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public AnthropicModelClient build() {
            return new AnthropicModelClient(this);
        }
    }
}
