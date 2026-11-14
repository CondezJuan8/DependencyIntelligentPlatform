package com.dependencyintelligence.sbom.application.exception;

public final class InvalidSbomException extends RuntimeException {
    public enum Reason {
        INVALID_JSON, UNSUPPORTED_FORMAT, UNSUPPORTED_VERSION, INVALID_COMPONENT,
        DUPLICATE_REFERENCE, LIMIT_EXCEEDED, READ_FAILED
    }

    private final Reason reason;

    public InvalidSbomException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public InvalidSbomException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
