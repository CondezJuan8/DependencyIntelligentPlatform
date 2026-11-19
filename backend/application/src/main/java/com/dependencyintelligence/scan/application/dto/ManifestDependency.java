package com.dependencyintelligence.scan.application.dto;

import com.dependencyintelligence.component.domain.PackageCoordinates;

/** One dependency explicitly declared by a Maven module. */
public record ManifestDependency(String manifestPath, PackageCoordinates coordinates, String scope, String dependencyType) {
    public ManifestDependency(String manifestPath, PackageCoordinates coordinates, String scope) {
        this(manifestPath, coordinates, scope, "DIRECT");
    }
    public ManifestDependency {
        if (manifestPath == null || manifestPath.isBlank() || manifestPath.length() > 240)
            throw new IllegalArgumentException("Invalid manifest path");
        if (coordinates == null) throw new IllegalArgumentException("Package coordinates are required");
        if (scope == null || scope.isBlank()) throw new IllegalArgumentException("Dependency scope is required");
        if (!"DIRECT".equals(dependencyType) && !"TRANSITIVE".equals(dependencyType)) throw new IllegalArgumentException("Invalid dependency type");
    }
}
