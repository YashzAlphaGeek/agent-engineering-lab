package com.catalyst.tools;

import com.catalyst.domain.Job;

import java.util.List;

public class JobSearchTool
        implements BaseTool {

    @Override
    public String getName() {
        return "job_search";
    }

    @Override
    public Object execute(Object input) {

        return List.of(
                new Job(
                        "Senior Python Developer",
                        "TechCorp",
                        "Munich",
                        List.of("Python", "FastAPI")
                )
        );
    }
}