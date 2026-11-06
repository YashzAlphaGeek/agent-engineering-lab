package com.catalyst.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowCoordinatorIntegrationTest {

    @Test
    void shouldExecuteWorkflowAndCreateHandoff() {

        JobApplicationWorkflow workflow =
                new JobApplicationWorkflow();

        workflow.executeWorkflow();

        assertNotNull(
                workflow.getState()
                        .getHandoff()
        );

        assertTrue(

                workflow.getState()
                        .isCompleted(
                                WorkflowStep.SEARCH_JOBS
                        )
        );

        assertTrue(

                workflow.getState()
                        .isCompleted(
                                WorkflowStep.ANALYZE_RESUME
                        )
        );

        assertTrue(

                workflow.getState()
                        .isCompleted(
                                WorkflowStep.MATCH_SKILLS
                        )
        );

        assertTrue(

                workflow.getState()
                        .isCompleted(
                                WorkflowStep.GENERATE_RECOMMENDATION
                        )
        );
    }
}