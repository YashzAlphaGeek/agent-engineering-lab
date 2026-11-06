package com.catalyst.workflow;

public record WorkflowExecutionResult(

        WorkflowStep step,

        boolean success,

        String output

) {
}