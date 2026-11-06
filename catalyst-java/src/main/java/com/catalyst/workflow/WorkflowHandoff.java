package com.catalyst.workflow;

public record WorkflowHandoff(

        String workflowId,

        String originalGoal,

        String conversationSummary,

        String rootCauseAnalysis,

        String recommendedAction

) {
}