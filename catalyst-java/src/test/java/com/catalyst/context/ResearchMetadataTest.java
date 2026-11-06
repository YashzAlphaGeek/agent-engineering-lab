package com.catalyst.context;

import com.catalyst.model.ResearchMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResearchMetadataTest {

    @Test
    void shouldCreateMetadata() {

        ResearchMetadata metadata =
                new ResearchMetadata(

                        "https://jobs.example.com",

                        "JobsDataset",

                        null,

                        "HIGH",

                        "JobSearchAgent"
                );

        assertEquals(
                "HIGH",
                metadata.confidence()
        );

        assertEquals(
                "JobSearchAgent",
                metadata.retrievedBy()
        );
    }
}