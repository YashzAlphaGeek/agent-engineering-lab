package com.catalyst.phases;

import com.catalyst.agents.JobSearchAgent;
import com.catalyst.context.ContextBuilder;
import com.catalyst.model.ResearchContext;
import com.catalyst.model.ResearchFinding;

public class Phase4Runner {

    public void run() {

        System.out.println(
                "\n===================================="
        );

        System.out.println(
                "PHASE 4 - CONTEXT PASSING"
        );

        System.out.println(
                "====================================\n"
        );

        ContextBuilder builder =
                new ContextBuilder();

        ResearchContext context =
                builder.build(

                        "Find Java jobs in Munich",

                        "Job Search",

                        """
                        Previous findings:

                        Market demand is increasing.
                        Salary ranges are competitive.
                        """
                );

        JobSearchAgent agent =
                new JobSearchAgent();

        ResearchFinding finding =
                agent.research(
                        context
                );

        System.out.println(
                "Original Goal:"
        );

        System.out.println(
                context.originalGoal()
        );

        System.out.println(
                "\nAssigned SubTopic:"
        );

        System.out.println(
                context.assignedSubTopic()
        );

        System.out.println(
                "\nExpected Output:"
        );

        System.out.println(
                context.expectedOutput()
        );

        System.out.println(
                "\nPrior Findings:"
        );

        System.out.println(
                context.priorFindings()
        );

        System.out.println(
                "\n===== RESEARCH FINDING =====\n"
        );

        System.out.println(
                "Claim: "
                        + finding.claim()
        );

        System.out.println(
                "Analysis: "
                        + finding.analysis()
        );

        System.out.println(
                "\n===== METADATA =====\n"
        );

        System.out.println(
                "Source URL: "
                        + finding.metadata()
                                 .sourceUrl()
        );

        System.out.println(
                "Document: "
                        + finding.metadata()
                                 .documentName()
        );

        System.out.println(
                "Page: "
                        + finding.metadata()
                                 .pageNumber()
        );

        System.out.println(
                "Confidence: "
                        + finding.metadata()
                                 .confidence()
        );

        System.out.println(
                "Retrieved By: "
                        + finding.metadata()
                                 .retrievedBy()
        );
    }
}
