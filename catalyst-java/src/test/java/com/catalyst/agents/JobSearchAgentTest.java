package com.catalyst.agents;

import com.catalyst.model.ResearchContext;
import com.catalyst.model.ResearchFinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobSearchAgentTest {

    @Test
    void shouldReturnFinding() {

        JobSearchAgent agent =
                new JobSearchAgent();

        ResearchContext context =

                new ResearchContext(

                        "Find Java jobs",

                        "Job Search",

                        "Provide structured findings",

                        "None"
                );

        ResearchFinding finding =
                agent.research(
                        context
                );

        assertNotNull(finding);

        assertNotNull(
                finding.claim()
        );

        assertNotNull(
                finding.analysis()
        );

        assertNotNull(
                finding.metadata()
        );

        assertEquals(
                "JobSearchAgent",
                finding.metadata()
                      .retrievedBy()
        );

        assertEquals(
                "HIGH",
                finding.metadata()
                      .confidence()
        );
    }
}