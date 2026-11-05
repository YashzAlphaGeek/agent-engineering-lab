package com.catalyst.anthropic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StopReasonTest {

    @Test
    void shouldContainToolUse() {

        assertEquals(
                StopReason.TOOL_USE,
                StopReason.valueOf("TOOL_USE")
        );
    }

    @Test
    void shouldContainEndTurn() {

        assertEquals(
                StopReason.END_TURN,
                StopReason.valueOf("END_TURN")
        );
    }
}