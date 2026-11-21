package com.dependencyintelligence.sbom.application.dto;

/** A source-backed dependency relation between two occurrences in one imported SBOM. */
public record SbomDependencyEdge(String parentBomRef, String childBomRef) { }
