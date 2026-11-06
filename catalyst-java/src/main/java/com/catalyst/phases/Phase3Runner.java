package com.catalyst.phases;

import com.catalyst.agents.CoordinatorAgent;
import com.catalyst.model.ResearchFinding;
import com.catalyst.model.ResearchReport;

public class Phase3Runner {

    public void run() {

        System.out.println(
                "\n===================================="
        );

        System.out.println(
                "PHASE 3 - MULTI AGENT ORCHESTRATION"
        );

        System.out.println(
                "====================================\n"
        );

        CoordinatorAgent coordinator =
                new CoordinatorAgent();

        ResearchReport report =
                coordinator.research(
                        "Find Java jobs in Munich and recommend the best opportunities"
                );

        System.out.println(
                "\n===== FINAL RESEARCH REPORT =====\n"
        );

        System.out.println(
                "Topic: "
                        + report.topic()
        );

        System.out.println(
                "\nFindings:\n"
        );

        for (ResearchFinding finding :
        report.findings()) {

    System.out.println(
            "Claim : "
                    + finding.claim()
    );

    System.out.println(
            "Analysis : "
                    + finding.analysis()
    );

    System.out.println(
            "Retrieved By : "
                    + finding.metadata()
                             .retrievedBy()
    );

    System.out.println(
            "Confidence : "
                    + finding.metadata()
                             .confidence()
    );

    System.out.println(
            "Source URL : "
                    + finding.metadata()
                             .sourceUrl()
    );

    System.out.println(
            "Document : "
                    + finding.metadata()
                             .documentName()
    );

    System.out.println(
            "Page : "
                    + finding.metadata()
                             .pageNumber()
    );

    System.out.println(
            "----------------------------------"
    );
}

        System.out.println(
                "\n===== REPORT COMPLETE ====="
        );

        System.out.println(
                "Sections Generated: "
                        + report.findings().size()
        );
    }
}