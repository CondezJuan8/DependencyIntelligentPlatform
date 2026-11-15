package com.dependencyintelligence.identity.application.exception;

public class PermissionDeniedException extends RuntimeException {
    public PermissionDeniedException() { super("No tienes permiso para realizar esta operación"); }
}
