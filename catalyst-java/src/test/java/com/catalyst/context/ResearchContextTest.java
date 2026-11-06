package com.catalyst.context;

import com.catalyst.model.ResearchContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResearchContextTest {

    @Test
    void shouldCreateResearchContext() {

        ResearchContext context =
                new ResearchContext(

                        "Find Java jobs",

                        "Job Search",

                        "Structured findings",

                        "None"
                );

        assertEquals(
                "Find Java jobs",
                context.originalGoal()
        );

        assertEquals(
                "Job Search",
                context.assignedSubTopic()
        );
    }
}