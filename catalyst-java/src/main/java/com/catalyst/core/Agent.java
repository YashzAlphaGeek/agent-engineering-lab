package com.catalyst.core;

import com.catalyst.tools.BaseTool;
import com.catalyst.tools.ToolRegistry;

public class Agent {

    private final Planner planner;
    private final Executor executor;
    private final Evaluator evaluator;
    private final ToolRegistry registry;

    public Agent(
            Planner planner,
            Executor executor,
            Evaluator evaluator,
            ToolRegistry registry
    ) {
        this.planner = planner;
        this.executor = executor;
        this.evaluator = evaluator;
        this.registry = registry;
    }

    public Object run(
            String goal
    ) {

        String toolName =
                planner.createPlan(goal);

        BaseTool tool =
                registry.get(toolName);

        Object result =
                executor.execute(
                        tool,
                        goal
                );

        evaluator.evaluate(result);

        return result;
    }
}