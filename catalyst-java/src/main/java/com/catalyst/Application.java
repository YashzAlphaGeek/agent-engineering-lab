package com.catalyst;

import com.catalyst.phases.Phase1Runner;
import com.catalyst.phases.Phase2Runner;
import com.catalyst.phases.Phase3Runner;
import com.catalyst.phases.Phase4Runner;
import com.catalyst.phases.Phase5Runner;

public class Application {

    private static final String ACTIVE_PHASE =
            "PHASE_5";

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

            case "PHASE_3" -> {
                new Phase3Runner().run();
            }

            case "PHASE_4" -> {
                new Phase4Runner().run();
            }

            case "PHASE_5" -> {
                new Phase5Runner().run();
            }

            default ->
                    throw new IllegalArgumentException(
                            "Unknown Phase: "
                                    + ACTIVE_PHASE
                    );
        }
    }
}