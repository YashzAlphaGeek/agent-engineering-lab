package com.catalyst;

import com.catalyst.core.*;
import com.catalyst.tools.*;

public class Application {

    public static void main(
            String[] args
    ) {

        ToolRegistry registry =
        new ToolRegistry();

registry.register(
        new JobSearchTool()
);

registry.register(
        new ResumeAnalyzerTool()
);

        registry.register(
                new CalculatorTool()
        );

        registry.register(
                new WebSearchTool()
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

        AgentState result =
                agent.run(
                    "search java jobs"
                );

        System.out.println(
                result.getResult()
        );
    }
}