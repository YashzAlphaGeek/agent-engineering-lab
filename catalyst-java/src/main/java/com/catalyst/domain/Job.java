package com.catalyst.domain;

import java.util.List;

public record Job(
        String title,
        String company,
        String location,
        List<String> skills
) {
}