package com.catalyst.anthropic;

public record ClaudeResponse(

        StopReason stopReason,

        ToolCall toolCall,

        String text

) {
}