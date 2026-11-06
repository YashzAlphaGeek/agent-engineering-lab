package com.catalyst.agents;

import com.catalyst.model.ResearchContext;
import com.catalyst.model.ResearchFinding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillsMatchAgentTest {

    @Test
    void shouldReturnFinding() {

        SkillsMatchAgent agent =
                new SkillsMatchAgent();

        ResearchContext context =
                new ResearchContext(

                        "Find Java jobs",

                        "Skills Match",

                        "Provide findings",

                        "Resume analysis complete"
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
                "SkillsMatchAgent",
                finding.metadata()
                       .retrievedBy()
        );
    }
}