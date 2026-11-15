package com.dependencyintelligence.identity.application.exception;

public class InvalidAccountTokenException extends RuntimeException {
    public InvalidAccountTokenException() { super("El enlace no es válido o ha caducado. Solicita uno nuevo."); }
}
