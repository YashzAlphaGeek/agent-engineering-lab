package com.catalyst.tools;

public interface BaseTool {

    ToolDefinition definition();

    Object execute(
            Object input
    );
}