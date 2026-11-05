package com.catalyst.phases;

import com.catalyst.anthropic.ClaudeResponse;
import com.catalyst.anthropic.MockClaudeClient;
import com.catalyst.anthropic.StopReason;
import com.catalyst.anthropic.ToolCall;
import com.catalyst.anthropic.ToolResult;
import com.catalyst.core.AgentState;
import com.catalyst.tools.BaseTool;
import com.catalyst.tools.JobSearchTool;
import com.catalyst.tools.ResumeAnalyzerTool;
import com.catalyst.tools.ToolRegistry;

public class Phase2Runner {

    private static final int MAX_ITERATIONS = 20;

    public void run() {

        System.out.println(
                "\n===== PHASE 2 : CLAUDE TOOL LOOP =====\n"
        );

        ToolRegistry registry =
                new ToolRegistry();

        registry.register(
                new JobSearchTool()
        );

        registry.register(
                new ResumeAnalyzerTool()
        );

        AgentState state =
                new AgentState();

        state.setGoal(
                """
                Find Java jobs in Munich and
                analyze the required skills
                """
        );

        MockClaudeClient client =
                new MockClaudeClient();

        while (!state.isCompleted()) {

            if (state.getIterationCount()
                    >= MAX_ITERATIONS) {

                System.out.println(
                        "WARNING: Safety cap reached."
                );

                break;
            }

            state.incrementIterationCount();

            ClaudeResponse response =
                    client.nextResponse();

            state.setStopReason(
                    response.stopReason()
                            .name()
            );

            System.out.println(
                    "\nIteration: "
                            + state.getIterationCount()
            );

            System.out.println(
                    "Stop Reason: "
                            + response.stopReason()
            );

            if (response.stopReason()
                    == StopReason.TOOL_USE) {

                ToolCall toolCall =
                        response.toolCall();

                System.out.println(
                        "Tool Requested: "
                                + toolCall.toolName()
                );

                BaseTool tool =
                        registry.get(
                                toolCall.toolName()
                        );

                if (tool == null) {

                    System.out.println(
                            "Tool not found: "
                                    + toolCall.toolName()
                    );

                    break;
                }

                Object toolOutput =
                        tool.execute(
                                toolCall.input()
                        );

                ToolResult toolResult =
                        new ToolResult(
                                toolCall.id(),
                                String.valueOf(
                                        toolOutput
                                )
                        );

                state.addHistory(
                        toolResult
                );

                state.setResult(
                        toolOutput
                );

                System.out.println(
                        "Tool Output:"
                );

                System.out.println(
                        toolOutput
                );

                continue;
            }

            if (response.stopReason()
                    == StopReason.END_TURN) {

                state.setFinalResponse(
                        response.text()
                );

                state.setCompleted(
                        true
                );

                System.out.println(
                        "\nClaude Final Response:"
                );

                System.out.println(
                        response.text()
                );
            }
        }

        printSummary(state);
    }

    private void printSummary(
            AgentState state
    ) {

        System.out.println(
                "\n================================="
        );

        System.out.println(
                "EXECUTION SUMMARY"
        );

        System.out.println(
                "================================="
        );

        System.out.println(
                "Iterations : "
                        + state.getIterationCount()
        );

        System.out.println(
                "Stop Reason : "
                        + state.getStopReason()
        );

        System.out.println(
                "Completed : "
                        + state.isCompleted()
        );

        System.out.println(
                "\nHistory:"
        );

        state.getHistory()
                .forEach(
                        System.out::println
                );

        System.out.println(
                "\nFinal Response:"
        );

        System.out.println(
                state.getFinalResponse()
        );
    }
}