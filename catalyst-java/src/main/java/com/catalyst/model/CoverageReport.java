package com.catalyst.model;

import java.util.List;

public record CoverageReport(

        List<String> covered,

        List<String> gaps,

        double completeness

) {
}