package com.catalyst.core;

import com.catalyst.anthropic.*;
import com.catalyst.tools.*;

public class AgentLoopV2 {

    private static final int
            MAX_ITERATIONS = 20;

    private final MockClaudeClient client;

    private final ToolRegistry registry;

    public AgentLoopV2(
            MockClaudeClient client,
            ToolRegistry registry
    ) {

        this.client = client;
        this.registry = registry;
    }

    public AgentState run(
            AgentState state
    ) {

        while (true) {

            if (state.getIterationCount()
                    >= MAX_ITERATIONS) {

                System.out.println(
                        "WARNING: Safety cap reached"
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

            if (
                    response.stopReason()
                            == StopReason.END_TURN
            ) {

                state.setFinalResponse(
                        response.text()
                );

                state.setCompleted(
                        true
                );

                break;
            }

            if (
                    response.stopReason()
                            == StopReason.TOOL_USE
            ) {

                ToolCall toolCall =
                        response.toolCall();

                BaseTool tool =
                        registry.get(
                                toolCall.toolName()
                        );

                Object result =
                        tool.execute(
                                toolCall.input()
                        );

                state.addHistory(
                        result
                );

                state.setResult(
                        result
                );
            }
        }

        return state;
    }
}