package com.catalyst;

import com.catalyst.tools.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    @Test
    void shouldFindRegisteredTool() {

        ToolRegistry registry =
                new ToolRegistry();

        registry.register(
                new JobSearchTool()
        );

        BaseTool tool =
                registry.get(
                        "job_search"
                );

        assertNotNull(tool);
    }
}
