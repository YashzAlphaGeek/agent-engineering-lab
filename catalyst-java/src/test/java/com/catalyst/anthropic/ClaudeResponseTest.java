package com.catalyst.anthropic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeResponseTest {

    @Test
    void shouldCreateToolUseResponse() {

        ClaudeResponse response =
                new ClaudeResponse(

                        StopReason.TOOL_USE,

                        new ToolCall(
                                "1",
                                "job_search",
                                "java jobs"
                        ),

                        null
                );

        assertEquals(
                StopReason.TOOL_USE,
                response.stopReason()
        );

        assertNotNull(
                response.toolCall()
        );
    }
}