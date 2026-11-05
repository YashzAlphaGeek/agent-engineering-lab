package com.catalyst.core;

import com.catalyst.tools.BaseTool;

public class Executor {

    public Object execute(
            BaseTool tool,
            Object request
    ) {
        return tool.execute(request);
    }
}