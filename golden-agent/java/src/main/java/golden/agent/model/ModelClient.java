package golden.agent.model;

/**
 * Element 1, the model. The only place an LLM is reached.
 *
 * <p>Implement this once per provider (Anthropic API, Bedrock, Vertex, a test double).
 * The loop depends on this interface only, so providers are interchangeable.
 */
@FunctionalInterface
public interface ModelClient {

    ModelResponse send(ModelRequest request) throws ModelException;
}
