package com.dependencyintelligence.sbom.application.exception;

public final class ConflictingPackageException extends RuntimeException {
    public ConflictingPackageException() { super("PURL is already registered with different coordinates"); }
}
