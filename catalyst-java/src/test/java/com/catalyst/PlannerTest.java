package com.catalyst;

import com.catalyst.core.AgentState;
import com.catalyst.core.Planner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlannerTest {

    @Test
    void shouldSelectJobSearchTool() {

        AgentState state =
                new AgentState();

        state.setGoal(
                "search java jobs"
        );

        Planner planner =
                new Planner();

        String tool =
                planner.createPlan(
                        state
                );

        assertEquals(
                "job_search",
                tool
        );
    }
}
