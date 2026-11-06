package com.catalyst.agents;

import com.catalyst.model.ResearchContext;
import com.catalyst.model.ResearchFinding;
import com.catalyst.model.ResearchMetadata;

public class SkillsMatchAgent {

    public ResearchFinding research(
            ResearchContext context
    ) {

        return new ResearchFinding(

                "Strong Java backend alignment",

                """
                Skills match completed.

                Original Goal:
                %s

                Assigned Subtopic:
                %s

                Prior Findings:
                %s
                """
                .formatted(
                        context.originalGoal(),
                        context.assignedSubTopic(),
                        context.priorFindings()
                ),

                new ResearchMetadata(

                        null,

                        "SkillMatchingModel",

                        null,

                        "HIGH",

                        "SkillsMatchAgent"
                )
        );
    }
}