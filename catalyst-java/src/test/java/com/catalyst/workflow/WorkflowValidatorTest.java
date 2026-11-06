package com.catalyst.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowValidatorTest {

    @Test
    void shouldBlockSkillMatchBeforeResumeAnalysis() {

        WorkflowValidator validator =
                new WorkflowValidator();

        WorkflowState state =
                new WorkflowState();

        state.complete(
                WorkflowStep.SEARCH_JOBS
        );

        assertFalse(

                validator.canExecute(

                        WorkflowStep.MATCH_SKILLS,

                        state
                )
        );
    }

    @Test
    void shouldAllowResumeAnalysisAfterSearchJobs() {

        WorkflowValidator validator =
                new WorkflowValidator();

        WorkflowState state =
                new WorkflowState();

        state.complete(
                WorkflowStep.SEARCH_JOBS
        );

        assertTrue(

                validator.canExecute(

                        WorkflowStep.ANALYZE_RESUME,

                        state
                )
        );
    }
}