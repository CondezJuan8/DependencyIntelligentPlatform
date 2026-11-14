package com.dependencyintelligence.shared.application;

public final class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource) { super(resource + " not found"); }
}
