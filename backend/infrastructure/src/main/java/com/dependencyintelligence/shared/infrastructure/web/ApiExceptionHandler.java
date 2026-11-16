package com.dependencyintelligence.shared.infrastructure.web;

import com.dependencyintelligence.repository.application.exception.RepositoryAlreadyExistsException;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.identity.application.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(com.dependencyintelligence.organization.application.exception.AccessConflictException.class)
    public ProblemDetail accessConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
    @ExceptionHandler(ExternalIdentityException.class)
    public ProblemDetail externalIdentity(ExternalIdentityException exception) {
        String detail = switch (exception.reason()) {
            case PROVIDER_UNAVAILABLE -> "Este método de acceso no está disponible actualmente.";
            case EMAIL_VERIFICATION_REQUIRED -> "Verifica tu correo antes de vincular otro método de acceso.";
            case SESSION_EXPIRED -> "Vuelve a iniciar sesión para modificar tus métodos de acceso.";
            case LAST_SIGN_IN_METHOD -> "Conserva al menos un método de acceso disponible.";
            case ALREADY_AUTHENTICATED -> "Utiliza la opción Vincular de tu cuenta para añadir otro método de acceso.";
            default -> "No se pudo completar la operación con el proveedor.";
        };
        var status = exception.reason() == ExternalIdentityException.Reason.PROVIDER_UNAVAILABLE
            ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.CONFLICT;
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", exception.reason().name().toLowerCase(java.util.Locale.ROOT));
        return problem;
    }
    @ExceptionHandler(InvalidAccountTokenException.class)
    public ProblemDetail invalidToken(InvalidAccountTokenException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
    @ExceptionHandler(AccountMailUnavailableException.class)
    public ProblemDetail mailUnavailable(AccountMailUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }
    @ExceptionHandler(org.springframework.core.task.TaskRejectedException.class)
    public ProblemDetail recoveryBusy(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes. Inténtalo más tarde.");
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ProblemDetail invalidBody(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revisa los campos obligatorios y sus límites.");
    }
    @ExceptionHandler(AuthenticationFailedException.class)
    public ProblemDetail authenticationFailed(AuthenticationFailedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }
    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ProblemDetail accountConflict(AccountAlreadyExistsException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
    @ExceptionHandler(PermissionDeniedException.class)
    public ProblemDetail forbidden(PermissionDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail notFound(ResourceNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(RepositoryAlreadyExistsException.class)
    public ProblemDetail conflict(RepositoryAlreadyExistsException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidRequest(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
