package com.catalyst.agents;

import com.catalyst.model.ResearchContext;
import com.catalyst.model.ResearchFinding;
import com.catalyst.model.ResearchMetadata;

public class ResumeAnalysisAgent {

    public ResearchFinding research(
            ResearchContext context
    ) {

        return new ResearchFinding(

                "Spring Boot experience missing",

                """
                Resume analysis completed.

                Original Goal:
                %s

                Assigned Subtopic:
                %s
                """
                .formatted(
                        context.originalGoal(),
                        context.assignedSubTopic()
                ),

                new ResearchMetadata(

                        null,

                        "Resume.pdf",

                        3,

                        "MEDIUM",

                        "ResumeAnalysisAgent"
                )
        );
    }
}