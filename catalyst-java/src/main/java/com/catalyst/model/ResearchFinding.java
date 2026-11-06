package com.catalyst.model;

public record ResearchFinding(

        String claim,

        String analysis,

        ResearchMetadata metadata

) {
}