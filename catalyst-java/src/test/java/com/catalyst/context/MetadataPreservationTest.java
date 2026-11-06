package com.catalyst.context;

import com.catalyst.agents.JobSearchAgent;
import com.catalyst.model.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetadataPreservationTest {

    @Test
    void shouldPreserveMetadata() {

        JobSearchAgent agent =
                new JobSearchAgent();

        ResearchFinding finding =
                agent.research(

                        new ResearchContext(

                                "Find Java jobs",

                                "Job Search",

                                "Provide findings",

                                ""
                        )
                );

        assertNotNull(
                finding.metadata()
        );

        assertNotNull(
                finding.metadata()
                       .confidence()
        );

        assertNotNull(
                finding.metadata()
                       .retrievedBy()
        );
    }
}