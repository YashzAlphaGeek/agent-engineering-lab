package com.catalyst;

import com.catalyst.core.*;
import com.catalyst.tools.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentTest {

    @Test
    void shouldFindJobs() {

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

        Agent agent =
                new Agent(loop);

        AgentState state =
                agent.run(
                        "search java jobs"
                );

        assertTrue(
                state.isCompleted()
        );

        assertNotNull(
                state.getResult()
        );
    }
}