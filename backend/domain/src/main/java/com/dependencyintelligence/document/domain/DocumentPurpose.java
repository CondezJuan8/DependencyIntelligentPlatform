package com.dependencyintelligence.document.domain;

public enum DocumentPurpose {
    GENERAL, MASTER_CONTEXT, VULNERABILITY_REFERENCE;

    public static DocumentPurpose from(String value) {
        try { return value == null || value.isBlank() ? GENERAL : valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("El propósito del documento no es válido."); }
    }
}
