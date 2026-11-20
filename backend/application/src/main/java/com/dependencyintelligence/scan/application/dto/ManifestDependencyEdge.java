package com.dependencyintelligence.scan.application.dto;

/** A package-to-package relationship observed in a dependency lockfile. */
public record ManifestDependencyEdge(String parentManifestPath, String childManifestPath) {
    public ManifestDependencyEdge {
        if (parentManifestPath == null || parentManifestPath.isBlank() || parentManifestPath.length() > 512)
            throw new IllegalArgumentException("Invalid parent dependency reference");
        if (childManifestPath == null || childManifestPath.isBlank() || childManifestPath.length() > 512)
            throw new IllegalArgumentException("Invalid child dependency reference");
        if (parentManifestPath.equals(childManifestPath)) throw new IllegalArgumentException("A dependency cannot depend on itself");
    }
}
