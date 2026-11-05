package com.catalyst.anthropic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallTest {

    @Test
    void shouldCreateToolCall() {

        ToolCall call =
                new ToolCall(
                        "1",
                        "job_search",
                        "java jobs munich"
                );

        assertEquals(
                "job_search",
                call.toolName()
        );

        assertEquals(
                "java jobs munich",
                call.input()
        );
    }
}