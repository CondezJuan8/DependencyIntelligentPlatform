package com.dependencyintelligence.shared.infrastructure.web;

import com.dependencyintelligence.repository.application.exception.RepositoryAlreadyExistsException;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
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
