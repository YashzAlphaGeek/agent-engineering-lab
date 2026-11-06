package com.catalyst.agents;

import com.catalyst.model.ResearchContext;
import com.catalyst.model.ResearchFinding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResumeAnalysisAgentTest {

    @Test
    void shouldReturnFinding() {

        ResumeAnalysisAgent agent =
                new ResumeAnalysisAgent();

        ResearchContext context =
                new ResearchContext(

                        "Find Java jobs",

                        "Resume Analysis",

                        "Provide findings",

                        "Jobs already identified"
                );

        ResearchFinding finding =
                agent.research(
                        context
                );

        assertNotNull(finding);

        assertEquals(
                "ResumeAnalysisAgent",
                finding.metadata()
                      .retrievedBy()
        );
    }
}