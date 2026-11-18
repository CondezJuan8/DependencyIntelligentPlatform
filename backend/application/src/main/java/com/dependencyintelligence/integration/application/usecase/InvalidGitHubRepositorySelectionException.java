package com.dependencyintelligence.integration.application.usecase;

public final class InvalidGitHubRepositorySelectionException extends RuntimeException {
    public InvalidGitHubRepositorySelectionException() { super("La selección contiene repositorios que no pertenecen a una instalación autorizada."); }
}
