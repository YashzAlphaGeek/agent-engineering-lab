package com.catalyst.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowHandoffTest {

    @Test
    void shouldCreateCompleteHandoff() {

        WorkflowHandoff handoff =

                new HandoffBuilder().build(

                        "WF-001",

                        "Find Java jobs",

                        "Job search completed",

                        "Resume lacks Spring Boot",

                        "Complete Spring Boot training"
                );

        assertNotNull(handoff);

        assertNotNull(
                handoff.workflowId()
        );

        assertNotNull(
                handoff.originalGoal()
        );

        assertNotNull(
                handoff.conversationSummary()
        );

        assertNotNull(
                handoff.rootCauseAnalysis()
        );

        assertNotNull(
                handoff.recommendedAction()
        );
    }
}