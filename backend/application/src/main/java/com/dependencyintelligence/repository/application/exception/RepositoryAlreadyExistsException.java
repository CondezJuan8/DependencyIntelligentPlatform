package com.dependencyintelligence.repository.application.exception;

public final class RepositoryAlreadyExistsException extends RuntimeException {
    public RepositoryAlreadyExistsException() { super("Repository already registered in this organization"); }
}
