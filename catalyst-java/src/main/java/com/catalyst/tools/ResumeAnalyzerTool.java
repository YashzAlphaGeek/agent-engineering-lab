package com.catalyst.tools;

import java.util.List;
import java.util.Map;

public class ResumeAnalyzerTool
        implements BaseTool {

    @Override
    public ToolDefinition definition() {

        return new ToolDefinition(

                "resume_analyzer",

                "Analyze resume text and extract skills",

                Map.of(

                        "type", "object",

                        "properties",
                        Map.of(
                                "resume",
                                Map.of(
                                        "type",
                                        "string"
                                )
                        ),

                        "required",
                        List.of(
                                "resume"
                        )
                )
        );
    }

    @Override
    public Object execute(
            Object input
    ) {

        return """
                Resume Analysis

                Skills Identified:
                - Java
                - Spring Boot
                - SQL
                """;
    }
}