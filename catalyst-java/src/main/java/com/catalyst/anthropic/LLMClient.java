package com.catalyst.anthropic;

/**
 * The "what next?" slot of the Claude tool loop.
 *
 * Implementations:
 *  - MockClaudeClient : scripted responses (learning / tests)
 *  - real SDK client  : client.messages.create() (later phase)
 *
 * AgentLoopV2 depends on this interface, so swapping the mock
 * for the real client requires no change to the loop.
 */
public interface LLMClient {

    ClaudeResponse nextResponse();
}
