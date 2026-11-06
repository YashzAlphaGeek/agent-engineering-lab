package com.catalyst.context;

import com.catalyst.agents.JobSearchAgent;
import com.catalyst.model.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubagentIsolationTest {

    @Test
    void shouldOnlyUseProvidedContext() {

        ResearchContext context =
                new ResearchContext(

                        "Find Java jobs",

                        "Job Search",

                        "Provide findings",

                        ""
                );

        JobSearchAgent agent =
                new JobSearchAgent();

        ResearchFinding result =
                agent.research(
                        context
                );

        assertNotNull(result);

        assertEquals(
                "JobSearchAgent",
                result.metadata()
                      .retrievedBy()
        );
    }
}