package com.dependencyintelligence.sbom.application.dto;

import java.util.List;
import java.util.Objects;

public record ParsedSbom(String format, String specificationVersion, String serialNumber, List<SbomComponent> components) {
    public ParsedSbom {
        Objects.requireNonNull(format, "Format is required");
        Objects.requireNonNull(specificationVersion, "Specification version is required");
        components = List.copyOf(components);
    }
}
