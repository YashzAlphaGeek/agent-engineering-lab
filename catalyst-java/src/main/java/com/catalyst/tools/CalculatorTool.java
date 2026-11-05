package com.catalyst.tools;

import java.util.List;
import java.util.Map;

public class CalculatorTool
        implements BaseTool {

    @Override
    public ToolDefinition definition() {

        return new ToolDefinition(

                "calculator",

                "Evaluates a mathematical expression",

                Map.of(
                        "type", "object",

                        "properties",
                        Map.of(
                                "expression",
                                Map.of(
                                        "type",
                                        "string"
                                )
                        ),

                        "required",
                        List.of(
                                "expression"
                        )
                )
        );
    }

    @Override
    public Object execute(
            Object input
    ) {

        return "42";
    }
}