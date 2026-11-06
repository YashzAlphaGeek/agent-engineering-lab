package com.catalyst.workflow;

public class WorkflowEngine {

    private final WorkflowValidator validator =
            new WorkflowValidator();

    public void execute(

            WorkflowStep step,

            WorkflowState state
    ) {

        if (!validator.canExecute(
                step,
                state
        )) {

            state.setStatus(
                    WorkflowStatus.BLOCKED
            );

            throw new IllegalStateException(

                    "Workflow step blocked: "

                            + step
            );
        }

        state.setStatus(
                WorkflowStatus.IN_PROGRESS
        );

        state.complete(step);

        state.setStatus(
                WorkflowStatus.COMPLETED
        );

        System.out.println(
                "Completed: " + step
        );
    }
}