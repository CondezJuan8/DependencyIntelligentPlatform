package com.dependencyintelligence.organization.domain;

import java.util.Locale;

public enum WorkspaceLanguage {
    ES, EN;

    public static WorkspaceLanguage parse(String value) {
        try { return valueOf(value == null ? "" : value.strip().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("El idioma del espacio no es válido."); }
    }
}
