package com.dependencyintelligence.identity.domain;

/** Personal names accept Unicode letters/marks, spaces, apostrophes and hyphens, never digits. */
public record PersonalName(String value) {
    public PersonalName {
        if (value == null) throw new IllegalArgumentException("El nombre es obligatorio");
        value = value.strip().replaceAll("\\s+", " ");
        if (value.length() > 200 || !value.matches("[\\p{L}\\p{M}]+(?:[ '\u2019-][\\p{L}\\p{M}]+)*")) {
            throw new IllegalArgumentException("El nombre solo puede contener letras, espacios, apóstrofos y guiones");
        }
    }
}
