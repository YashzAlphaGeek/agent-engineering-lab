package com.catalyst.core;

public class Agent {

    private final AgentLoop loop;

    public Agent(
            AgentLoop loop
    ) {

        this.loop = loop;
    }

    public AgentState run(
            String goal
    ) {

        AgentState state =
                new AgentState();

        state.setGoal(
                goal
        );

        return loop.run(
                state
        );
    }
}