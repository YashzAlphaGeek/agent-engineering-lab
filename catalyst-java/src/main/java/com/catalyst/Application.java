package com.catalyst;

import com.catalyst.phases.Phase1Runner;
import com.catalyst.phases.Phase2Runner;

public class Application {

    private static final String ACTIVE_PHASE =
            "PHASE_1";

    public static void main(
            String[] args
    ) {

        switch (ACTIVE_PHASE) {

            case "PHASE_1" -> {
                new Phase1Runner().run();
            }

            case "PHASE_2" -> {
                new Phase2Runner().run();
            }

            default ->
                    throw new IllegalArgumentException(
                            "Unknown Phase"
                    );
        }
    }
}