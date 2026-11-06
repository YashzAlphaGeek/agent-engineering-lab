package com.catalyst.agents;

import com.catalyst.model.ResearchReport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoordinatorAgentTest {

    @Test
    void shouldProduceResearchReport() {

        CoordinatorAgent coordinator =
                new CoordinatorAgent();

        ResearchReport report =
                coordinator.research(
                        "Find Java jobs in Munich"
                );

        assertNotNull(report);

        assertEquals(
                "Find Java jobs in Munich",
                report.topic()
        );

        assertEquals(
                5,
                report.findings().size()
        );
    }

    @Test
    void shouldCoverAllSubtopics() {

        CoordinatorAgent coordinator =
                new CoordinatorAgent();

        ResearchReport report =
                coordinator.research(
                        "Find Java jobs in Munich"
                );

        assertEquals(
                5,
                report.findings().size()
        );
    }

    @Test
void shouldGenerateAtLeastFiveAreas() {

    CoordinatorAgent coordinator =
            new CoordinatorAgent();

    ResearchReport report =
            coordinator.research(
                    "Career opportunities for Java developers"
            );

    assertTrue(
            report.findings().size() >= 5
    );
}
}