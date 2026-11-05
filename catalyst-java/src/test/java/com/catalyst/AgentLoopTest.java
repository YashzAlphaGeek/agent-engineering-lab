package com.catalyst;

import com.catalyst.core.*;
import com.catalyst.tools.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentLoopTest {

    @Test
    void shouldExecuteLoop() {

        ToolRegistry registry =
                new ToolRegistry();

        registry.register(
                new JobSearchTool()
        );

        AgentLoop loop =
                new AgentLoop(

                        new Planner(),

                        new Executor(),

                        new Evaluator(),

                        registry
                );

        AgentState state =
                new AgentState();

        state.setGoal(
                "search java jobs"
        );

        AgentState result =
                loop.run(state);

        assertTrue(
                result.isCompleted()
        );

        assertNotNull(
                result.getResult()
        );

        assertEquals(
                1,
                result.getIterationCount()
        );
    }
}