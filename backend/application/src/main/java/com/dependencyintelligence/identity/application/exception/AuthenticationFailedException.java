package com.dependencyintelligence.identity.application.exception;

public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() { super("Correo o contraseña incorrectos"); }
}
