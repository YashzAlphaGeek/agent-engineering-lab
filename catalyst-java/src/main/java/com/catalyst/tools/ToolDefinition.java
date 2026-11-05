package com.catalyst.tools;

import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema
) {
}