package com.catalyst.tools;

import java.util.List;
import java.util.Map;

public class JobSearchTool
        implements BaseTool {

    @Override
    public ToolDefinition definition() {

        return new ToolDefinition(

                "job_search",

                "Search jobs based on keywords and location",

                Map.of(

                        "type", "object",

                        "properties",
                        Map.of(
                                "query",
                                Map.of(
                                        "type",
                                        "string")),

                        "required",
                        List.of(
                                "query")));
    }

    @Override
    public Object execute(
            Object input) {

        String query = String.valueOf(
                input);

        return """
                Job Found

                Title: Senior Java Developer
                Company: TechCorp
                Location: Munich

                Search Query:
                """ + query;
    }
}