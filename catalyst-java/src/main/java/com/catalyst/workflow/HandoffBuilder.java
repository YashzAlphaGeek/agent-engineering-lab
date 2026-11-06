package com.catalyst.workflow;

public class HandoffBuilder {

    public WorkflowHandoff build(

            String workflowId,

            String originalGoal,

            String conversationSummary,

            String rootCauseAnalysis,

            String recommendedAction
    ) {

        return new WorkflowHandoff(

                workflowId,

                originalGoal,

                conversationSummary,

                rootCauseAnalysis,

                recommendedAction
        );
    }
}