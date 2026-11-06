package com.catalyst.model;

import java.time.Instant;
import java.util.List;

public record ResearchOutput(

        List<ResearchFinding> findings,

        String query,

        Instant timestamp

) {
}