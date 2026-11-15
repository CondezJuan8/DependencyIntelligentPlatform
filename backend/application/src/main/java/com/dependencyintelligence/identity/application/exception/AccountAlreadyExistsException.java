package com.dependencyintelligence.identity.application.exception;

public class AccountAlreadyExistsException extends RuntimeException {
    public AccountAlreadyExistsException() { super("No se puede registrar ese correo. Inicia sesión o utiliza otro correo."); }
}
