package com.catalyst.anthropic;

import com.catalyst.core.AgentLoopV2;
import com.catalyst.core.AgentState;
import com.catalyst.tools.JobSearchTool;
import com.catalyst.tools.ResumeAnalyzerTool;
import com.catalyst.tools.ToolRegistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SequentialToolExecutionTest {

    @Test
    void shouldExecuteMultipleToolsBeforeEndTurn() {

        ToolRegistry registry =
                new ToolRegistry();

        registry.register(
                new JobSearchTool()
        );

        registry.register(
                new ResumeAnalyzerTool()
        );

        AgentLoopV2 loop =
                new AgentLoopV2(

                        new MockClaudeClient(),

                        registry
                );

        AgentState state =
                new AgentState();

        state.setGoal(
                "Find Java jobs and analyse skills"
        );

        AgentState result =
                loop.run(
                        state
                );

        assertTrue(
                result.isCompleted()
        );

        assertEquals(
                "END_TURN",
                result.getStopReason()
        );

        assertEquals(
                3,
                result.getIterationCount()
        );

        assertEquals(
                2,
                result.getHistory().size()
        );

        assertNotNull(
                result.getFinalResponse()
        );
    }
}