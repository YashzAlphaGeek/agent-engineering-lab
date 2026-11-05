package com.catalyst.tools;

import java.util.HashMap;
import java.util.Map;

public class ToolRegistry {

    private final Map<String, BaseTool> tools = new HashMap<>();

    public void register(
            BaseTool tool) {

        tools.put(
                tool.definition().name(),
                tool);
    }

    public BaseTool get(
            String toolName) {

        return tools.get(
                toolName);
    }
}
