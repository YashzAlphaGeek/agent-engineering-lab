package com.catalyst.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentState {

    private String goal;

    private Map<String, Object> plan = new HashMap<>();

    private Object result;

    private boolean completed;

    private List<Object> history = new ArrayList<>();

    public AgentState() {
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public Map<String, Object> getPlan() {
        return plan;
    }

    public void setPlan(Map<String, Object> plan) {
        this.plan = plan;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public List<Object> getHistory() {
        return history;
    }

    public void addHistory(Object item) {
        this.history.add(item);
    }
}