package com.dependencyintelligence.document.domain;

import java.util.Locale;

public enum DocumentType {
    PDF("PDF"), DOCX("DOCX"), MARKDOWN("MARKDOWN"), TXT("TXT"), README("README"), OPENAPI("OPENAPI"), ADR("ADR"), CHANGELOG("CHANGELOG");
    private final String code;
    DocumentType(String code) { this.code = code; }
    public String code() { return code; }

    public static DocumentType fromFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\"))
            throw new IllegalArgumentException("El nombre del archivo no es válido.");
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return PDF;
        if (lower.endsWith(".docx")) return DOCX;
        if (lower.equals("readme") || lower.startsWith("readme.")) return README;
        if (lower.equals("changelog") || lower.startsWith("changelog.") || lower.startsWith("changes.")) return CHANGELOG;
        if (lower.endsWith(".adr") || lower.startsWith("adr-")) return ADR;
        if (lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".json")) return OPENAPI;
        if (lower.endsWith(".md")) return MARKDOWN;
        if (lower.endsWith(".txt")) return TXT;
        throw new IllegalArgumentException("Tipo no admitido. Sube PDF, DOCX, Markdown, TXT, README, OpenAPI, ADR o CHANGELOG.");
    }
}
