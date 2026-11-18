package com.dependencyintelligence.integration.application.usecase;

public final class InvalidGitHubInstallStateException extends RuntimeException {
    public InvalidGitHubInstallStateException() { super("La instalación de GitHub expiró o ya no corresponde a esta sesión. Iníciala otra vez."); }
}
