package com.catalyst.workflow;

import java.util.HashSet;
import java.util.Set;

public class WorkflowState {

    private final Set<WorkflowStep>
            completedSteps =
            new HashSet<>();
private WorkflowHandoff handoff;
    private WorkflowStatus status =
            WorkflowStatus.NOT_STARTED;

    public void complete(
            WorkflowStep step
    ) {

        completedSteps.add(step);
    }

    public boolean isCompleted(
            WorkflowStep step
    ) {

        return completedSteps.contains(step);
    }

    public WorkflowStatus getStatus() {

        return status;
    }

    public void setStatus(
            WorkflowStatus status
    ) {

        this.status = status;
    }

    public Set<WorkflowStep> getCompletedSteps() {

        return completedSteps;
    }

    public WorkflowHandoff getHandoff() {
    return handoff;
}

public void setHandoff(
        WorkflowHandoff handoff
) {
    this.handoff = handoff;
}
}