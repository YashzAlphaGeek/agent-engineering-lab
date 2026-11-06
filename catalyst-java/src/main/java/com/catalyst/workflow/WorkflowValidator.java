package com.catalyst.workflow;

public class WorkflowValidator {

    public boolean canExecute(

            WorkflowStep step,

            WorkflowState state
    ) {

        return switch (step) {

            case SEARCH_JOBS -> true;

            case ANALYZE_RESUME ->

                state.isCompleted(
                        WorkflowStep.SEARCH_JOBS
                );

            case MATCH_SKILLS ->

                state.isCompleted(
                        WorkflowStep.ANALYZE_RESUME
                );

            case GENERATE_RECOMMENDATION ->

                state.isCompleted(
                        WorkflowStep.MATCH_SKILLS
                );
        };
    }
}