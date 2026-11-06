package com.catalyst.agents;

import com.catalyst.model.ResearchContext;
import com.catalyst.model.ResearchFinding;
import com.catalyst.model.ResearchMetadata;

public class JobSearchAgent {

    public ResearchFinding research(
            ResearchContext context
    ) {

        return new ResearchFinding(

                "Java jobs found in Munich",

                """
                Matching jobs identified for goal:
                %s

                Assigned Subtopic:
                %s
                """
                .formatted(

                        context.originalGoal(),

                        context.assignedSubTopic()
                ),

                new ResearchMetadata(

                        "https://jobs.example.com",

                        "JobSearchDataset",

                        null,

                        "HIGH",

                        "JobSearchAgent"
                )
        );
    }
}