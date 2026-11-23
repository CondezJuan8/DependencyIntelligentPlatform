package com.dependencyintelligence.document.domain;

public record DocumentName(String value) {
    public DocumentName {
        if (value == null || value.isBlank() || value.strip().length() > 200)
            throw new IllegalArgumentException("El nombre debe tener entre 1 y 200 caracteres.");
        value = value.strip();
    }
}
