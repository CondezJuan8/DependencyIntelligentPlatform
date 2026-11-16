package com.dependencyintelligence.identity.application.exception;

public class ExternalIdentityException extends RuntimeException {
    public enum Reason { LINK_REQUIRED, IDENTITY_CONFLICT, ACCOUNT_DISABLED, EMAIL_REQUIRED,
        EMAIL_VERIFICATION_REQUIRED, SESSION_EXPIRED, LAST_SIGN_IN_METHOD, PROVIDER_UNAVAILABLE, ALREADY_AUTHENTICATED }
    private final Reason reason;

    public ExternalIdentityException(Reason reason) {
        super("No se pudo completar el acceso con el proveedor");
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
