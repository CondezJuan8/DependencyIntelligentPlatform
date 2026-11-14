package com.dependencyintelligence.sbom.application.dto;

import com.dependencyintelligence.component.domain.PackageCoordinates;
import java.util.Objects;

/** A snapshot occurrence, distinct from the package coordinates shared by several occurrences. */
public record SbomComponent(String bomRef, PackageCoordinates coordinates) {
    public SbomComponent {
        if (bomRef == null || bomRef.isBlank() || bomRef.length() > 512) {
            throw new IllegalArgumentException("Component reference is required and must fit 512 characters");
        }
        Objects.requireNonNull(coordinates, "Package coordinates are required");
    }
}
