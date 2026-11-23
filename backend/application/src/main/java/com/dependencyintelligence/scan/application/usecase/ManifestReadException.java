package com.dependencyintelligence.scan.application.usecase;

/** A problem reading a source or obtaining inventory from a scanner. */
public final class ManifestReadException extends RuntimeException {
    ManifestReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
