package com.catalyst.model;

public record ResearchMetadata(

        String sourceUrl,

        String documentName,

        Integer pageNumber,

        String confidence,

        String retrievedBy

) {
}