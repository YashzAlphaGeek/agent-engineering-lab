package com.catalyst.model;

public record ResearchContext(

        String originalGoal,

        String assignedSubTopic,

        String expectedOutput,

        String priorFindings

) {
}