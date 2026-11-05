package com.catalyst.anthropic;

public record ToolCall(

        String id,

        String toolName,

        String input

) {
}