package com.catalyst.context;

import com.catalyst.model.ResearchContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContextBuilderTest {

    @Test
    void shouldBuildContext() {

        ContextBuilder builder =
                new ContextBuilder();

        ResearchContext context =
                builder.build(

                        "Find Java jobs",

                        "Job Search",

                        "Previous findings"
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