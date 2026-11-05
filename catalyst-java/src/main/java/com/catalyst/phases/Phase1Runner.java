package com.catalyst.phases;

import com.catalyst.core.Agent;
import com.catalyst.core.AgentLoop;
import com.catalyst.core.AgentState;
import com.catalyst.core.Evaluator;
import com.catalyst.core.Executor;
import com.catalyst.core.Planner;
import com.catalyst.tools.JobSearchTool;
import com.catalyst.tools.ResumeAnalyzerTool;
import com.catalyst.tools.ToolRegistry;

public class Phase1Runner {

    public void run() {

        System.out.println(
                "\n===== PHASE 1 : AGENT ARCHITECTURE =====\n"
        );

        ToolRegistry registry =
                new ToolRegistry();

        registry.register(
                new JobSearchTool()
        );

        registry.register(
                new ResumeAnalyzerTool()
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
                "Iterations: "
                        + result.getIterationCount()
        );

        System.out.println(
                "\nResult:\n"
                        + result.getResult()
        );
    }
}