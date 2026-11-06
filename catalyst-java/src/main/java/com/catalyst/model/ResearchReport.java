package com.catalyst.model;

import java.util.List;

public record ResearchReport(

        String topic,

        List<ResearchFinding> findings

) {
}