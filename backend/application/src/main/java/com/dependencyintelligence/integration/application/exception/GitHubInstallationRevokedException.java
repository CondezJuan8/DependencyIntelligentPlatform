package com.dependencyintelligence.integration.application.exception;

/** GitHub returned 404 for an installation this app had previously connected. */
public final class GitHubInstallationRevokedException extends RuntimeException {
    public GitHubInstallationRevokedException() { super("La instalación ya no existe o fue revocada en GitHub."); }
}
