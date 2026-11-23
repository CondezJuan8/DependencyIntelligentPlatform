package com.dependencyintelligence.assistant.infrastructure;

public final class AiProviderUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public AiProviderUnavailableException(String message) { super(message); }
    public AiProviderUnavailableException(String message, Throwable cause) { super(message, cause); }
}
