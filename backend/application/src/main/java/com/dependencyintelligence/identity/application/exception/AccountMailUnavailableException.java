package com.dependencyintelligence.identity.application.exception;

public class AccountMailUnavailableException extends RuntimeException {
    public AccountMailUnavailableException() { super("No se pudo enviar el correo. Inténtalo de nuevo más tarde."); }
}
