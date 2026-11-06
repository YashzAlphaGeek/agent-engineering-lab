package com.catalyst.context;

import com.catalyst.agents.JobSearchAgent;
import com.catalyst.model.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContextPassingTest {

    @Test
    void shouldPassContextToSubagent() {

        JobSearchAgent agent =
                new JobSearchAgent();

        ResearchContext context =
                new ResearchContext(

                        "Find Java jobs",

                        "Job Search",

                        "Provide findings",

                        "Salary analysis complete"
                );

        ResearchFinding result =
                agent.research(
                        context
                );

        assertNotNull(result);

        assertNotNull(
                result.metadata()
        );

        assertEquals(
                "JobSearchAgent",
                result.metadata()
                      .retrievedBy()
        );
    }
}