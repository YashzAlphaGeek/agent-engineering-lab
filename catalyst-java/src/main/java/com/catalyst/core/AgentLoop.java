package com.catalyst.core;

import com.catalyst.tools.BaseTool;
import com.catalyst.tools.ToolRegistry;

public class AgentLoop {

    private static final int MAX_ITERATIONS = 20;

    private final Planner planner;

    private final Executor executor;

    private final Evaluator evaluator;

    private final ToolRegistry registry;

    public AgentLoop(
            Planner planner,
            Executor executor,
            Evaluator evaluator,
            ToolRegistry registry) {

        this.planner = planner;
        this.executor = executor;
        this.evaluator = evaluator;
        this.registry = registry;
    }

    public AgentState run(
            AgentState state) {

        while (!state.isCompleted()) {

            if (state.getIterationCount() >= MAX_ITERATIONS) {

                System.out.println(
                        "Safety cap reached");

                break;
            }

            state.incrementIterationCount();

            String toolName = planner.createPlan(
                    state);

            BaseTool tool = registry.get(
                    toolName);

            Object result = executor.execute(
                    tool,
                    state.getGoal());

            state.setResult(
                    result);

            state.addHistory(
                    result);

            boolean success = evaluator.evaluate(
                    result);

            state.setCompleted(
                    success);
        }

        return state;
    }
}