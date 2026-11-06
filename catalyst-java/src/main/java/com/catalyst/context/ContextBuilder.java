package com.catalyst.context;

import com.catalyst.model.ResearchContext;

public class ContextBuilder {

    public ResearchContext build(

            String goal,

            String subTopic,

            String priorFindings
    ) {

        return new ResearchContext(

                goal,

                subTopic,

                "Provide structured findings",

                priorFindings
        );
    }
}