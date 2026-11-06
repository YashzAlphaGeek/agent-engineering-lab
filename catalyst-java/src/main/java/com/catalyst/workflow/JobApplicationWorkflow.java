package com.catalyst.workflow;

import com.catalyst.agents.CoordinatorAgent;
import com.catalyst.model.ResearchReport;

public class JobApplicationWorkflow {

    private final WorkflowEngine engine =
            new WorkflowEngine();

    private final WorkflowState state =
            new WorkflowState();

    private final CoordinatorAgent coordinator =
            new CoordinatorAgent();

    public void executeWorkflow() {

        executeStep(
                WorkflowStep.SEARCH_JOBS,
                "Search Java jobs in Munich"
        );

        executeStep(
                WorkflowStep.ANALYZE_RESUME,
                "Analyse candidate resume"
        );

        executeStep(
                WorkflowStep.MATCH_SKILLS,
                "Match resume against job requirements"
        );

        executeStep(
                WorkflowStep.GENERATE_RECOMMENDATION,
                "Generate final recommendation"
        );

        createHandoff();
    }

    private void executeStep(

            WorkflowStep step,

            String goal
    ) {

        engine.execute(
                step,
                state
        );

        ResearchReport report =
                coordinator.research(
                        goal
                );

        System.out.println(
                "\nWorkflow Step: "
                        + step
        );

        System.out.println(
                "Generated Findings: "
                        + report.findings().size()
        );
    }

    private void createHandoff() {

        WorkflowHandoff handoff =

                new HandoffBuilder().build(

                        "WF-001",

                        "Find Java jobs in Munich",

                        """
                        Job search completed.
                        Resume analysed.
                        Skills matched.
                        Recommendation generated.
                        """,

                        """
                        Candidate lacks
                        advanced Spring Boot
                        experience.
                        """,

                        """
                        Apply for roles while
                        improving Spring Boot
                        skills.
                        """
                );

        state.setHandoff(
                handoff
        );

        System.out.println(
                "\n===== HANDOFF CREATED ====="
        );

        System.out.println(
                handoff
        );
    }

    public WorkflowState getState() {

        return state;
    }
}