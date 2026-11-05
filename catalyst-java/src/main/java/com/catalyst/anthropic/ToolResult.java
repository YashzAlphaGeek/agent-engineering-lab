package com.catalyst.anthropic;

public record ToolResult(

        String toolCallId,

        String content

) {
}