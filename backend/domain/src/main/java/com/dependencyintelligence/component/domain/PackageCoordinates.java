package com.dependencyintelligence.component.domain;

/** Value object identifying a concrete package version, independent of its snapshot occurrence. */
public record PackageCoordinates(String ecosystem, String namespace, String name, String version, String purl) {
    public PackageCoordinates {
        ecosystem = required(ecosystem, "Ecosystem", 100);
        namespace = required(namespace, "Namespace", 255);
        name = required(name, "Name", 512);
        version = required(version, "Version", 255);
        purl = required(purl, "PURL", 2048);
        if (!purl.startsWith("pkg:")) { throw new IllegalArgumentException("PURL must identify a package"); }
    }

    private static String required(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " is missing, too long or has surrounding whitespace");
        }
        return value;
    }
}
