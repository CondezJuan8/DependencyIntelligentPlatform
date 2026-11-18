package com.dependencyintelligence.organization.domain;

import java.util.Locale;

public enum WorkspaceTheme {
    SYSTEM, LIGHT, DARK;

    public static WorkspaceTheme parse(String value) {
        try { return valueOf(value == null ? "" : value.strip().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("El tema del espacio no es válido."); }
    }
}
