package com.catalyst.phases;

import com.catalyst.workflow.JobApplicationWorkflow;

public class Phase5Runner {

    public void run() {

        System.out.println(
                "\n===================================="
        );

        System.out.println(
                "PHASE 5 - WORKFLOW ENFORCEMENT"
        );

        System.out.println(
                "====================================\n"
        );

        JobApplicationWorkflow workflow =
                new JobApplicationWorkflow();

        workflow.executeWorkflow();
    }
}