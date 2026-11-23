package com.dependencyintelligence.inventory.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class InventoryCatalog {
    private InventoryCatalog() {}

    public record Filters(UUID repositoryId, String query, String ecosystem, String version,
                          String kind, String scope, String security, String severity, boolean riskFirst) {
        public Filters {
            query = text(query, 160);
            version = text(version, 255);
            ecosystem = option(ecosystem, "MAVEN", "NPM");
            kind = option(kind, "DIRECT", "TRANSITIVE", "UNKNOWN");
            scope = text(scope, 40).toLowerCase(Locale.ROOT);
            security = option(security, "VULNERABLE", "NO_KNOWN", "REVIEW");
            severity = option(severity, "CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN");
        }

        private static String text(String value, int maxLength) {
            String normalized = value == null ? "" : value.strip();
            if (normalized.length() > maxLength) throw new IllegalArgumentException("El filtro supera el tamaño permitido.");
            return normalized;
        }

        private static String option(String value, String... options) {
            String normalized = text(value, 32).toUpperCase(Locale.ROOT);
            if (normalized.isEmpty() || normalized.equals("ALL")) return "";
            if (!List.of(options).contains(normalized)) throw new IllegalArgumentException("El valor del filtro no es válido.");
            return normalized;
        }
    }

    public record Page(int number, int size) {
        public Page {
            if (number < 0 || size < 1 || size > 100 || number > Integer.MAX_VALUE / size)
                throw new IllegalArgumentException("La página solicitada no es válida.");
        }
        public int offset() { return number * size; }
    }

    public record PackageKey(String ecosystem, String namespace, String name) {
        public PackageKey {
            ecosystem = Filters.option(ecosystem, "MAVEN", "NPM");
            namespace = Filters.text(namespace, 255);
            name = Filters.text(name, 512);
            if (ecosystem.isEmpty() || name.isEmpty()) throw new IllegalArgumentException("Identifica el ecosistema y el paquete.");
        }
    }

    public record PackageSummary(PackageKey key, long versions, long repositories, long occurrences,
                                 long withAdvisories, String severity, long pending) {}

    public record CatalogPage(List<PackageSummary> packages, long totalPackages, long totalVersions) {}

    public record Occurrence(UUID id, UUID repositoryId, String repositoryName, String source, String version,
                             String manifestPath, String scope, String kind, UUID scanId, Instant analyzedAt,
                             String commit, String sourceSha256, String assessment, Instant checkedAt,
                             int advisoryCount, String severity) {}

    public record OccurrencePage(List<Occurrence> occurrences, long totalOccurrences) {}
}
