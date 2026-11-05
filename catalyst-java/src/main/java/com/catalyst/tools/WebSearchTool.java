package com.catalyst.tools;

import java.util.List;
import java.util.Map;

public class WebSearchTool
        implements BaseTool {

    @Override
    public ToolDefinition definition() {

        return new ToolDefinition(

                "web_search",

                "Search the web",

                Map.of(
                        "type", "object",

                        "properties",
                        Map.of(
                                "query",
                                Map.of(
                                        "type",
                                        "string"
                                )
                        ),

                        "required",
                        List.of(
                                "query"
                        )
                )
        );
    }

    @Override
    public Object execute(
            Object input
    ) {

        return """
                Mock search result
                """;
    }
}