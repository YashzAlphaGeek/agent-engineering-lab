package com.catalyst.anthropic;

public class MockClaudeClient {

    private int step = 0;

    public ClaudeResponse nextResponse() {

        step++;

        switch (step) {

            case 1:

                return new ClaudeResponse(

                        StopReason.TOOL_USE,

                        new ToolCall(
                                "1",
                                "job_search",
                                "java jobs munich"
                        ),

                        null
                );

            case 2:

                return new ClaudeResponse(

                        StopReason.TOOL_USE,

                        new ToolCall(
                                "2",
                                "resume_analyzer",
                                "java developer resume"
                        ),

                        null
                );

            case 3:

                return new ClaudeResponse(

                        StopReason.END_TURN,

                        null,

                        """
                        Found matching Java jobs
                        and analysed required skills.
                        """
                );

            default:

                return new ClaudeResponse(

                        StopReason.END_TURN,

                        null,

                        "Completed."
                );
        }
    }
}