package com.dependencyintelligence.sbom.application;

public final class SbomLimits {
    public static final int MAX_BYTES = 25 * 1024 * 1024;
    public static final int MAX_COMPONENTS = 50_000;

    private SbomLimits() { }
}
