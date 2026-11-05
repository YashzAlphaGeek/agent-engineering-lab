package com.catalyst.core;

import java.util.ArrayList;
import java.util.List;

public class AgentState {

    private String goal;

    private Object result;

    private boolean completed;

    private int iterationCount;

    private final List<Object>
            history =
            new ArrayList<>();

    public String getGoal() {
        return goal;
    }

    public void setGoal(
            String goal
    ) {
        this.goal = goal;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(
            Object result
    ) {
        this.result = result;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(
            boolean completed
    ) {
        this.completed = completed;
    }

    public int getIterationCount() {
        return iterationCount;
    }

    public void incrementIterationCount() {
        iterationCount++;
    }

    public List<Object> getHistory() {
        return history;
    }

    public void addHistory(
            Object item
    ) {
        history.add(item);
    }
}