package com.catalyst.core;

public class Planner {

    public String createPlan(
            AgentState state
    ) {

        String goal =
                state.getGoal()
                        .toLowerCase();

        if(goal.contains(
                "resume"
        )) {

            return "resume_analyzer";
        }

        return "job_search";
    }
}