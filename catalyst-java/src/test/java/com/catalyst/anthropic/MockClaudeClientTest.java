package com.catalyst.anthropic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockClaudeClientTest {

    @Test
    void shouldReturnToolUseFirst() {

        MockClaudeClient client =
                new MockClaudeClient();

        ClaudeResponse response =
                client.nextResponse();

        assertEquals(
                StopReason.TOOL_USE,
                response.stopReason()
        );
    }

    @Test
    void shouldReturnEndTurnEventually() {

        MockClaudeClient client =
                new MockClaudeClient();

        client.nextResponse();
        client.nextResponse();

        ClaudeResponse response =
                client.nextResponse();

        assertEquals(
                StopReason.END_TURN,
                response.stopReason()
        );
    }
}