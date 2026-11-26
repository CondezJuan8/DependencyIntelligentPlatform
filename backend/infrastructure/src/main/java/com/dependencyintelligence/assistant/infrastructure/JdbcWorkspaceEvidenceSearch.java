package com.dependencyintelligence.assistant.infrastructure;

import com.dependencyintelligence.assistant.application.dto.AssistantModels.Evidence;
import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.WorkspaceEvidenceSearch;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import com.dependencyintelligence.vulnerability.application.dto.VulnerabilityAssessmentModels.VersionAssessment;
import com.dependencyintelligence.vulnerability.application.port.out.VulnerabilityAssessmentStore;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Retrieves current scan and OSV facts only from repositories already authorized by the API. */
@Component
public final class JdbcWorkspaceEvidenceSearch implements WorkspaceEvidenceSearch {
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;
    private final VulnerabilityAssessmentStore assessments;

    public JdbcWorkspaceEvidenceSearch(JdbcTemplate jdbc, TenantTransactions tenants, VulnerabilityAssessmentStore assessments) {
        this.jdbc = jdbc; this.tenants = tenants; this.assessments = assessments;
    }

    @Override public List<Evidence> search(UUID organizationId, List<UUID> scanRepositoryIds,
                                           List<UUID> reportRepositoryIds, String question, int limit) {
        String normalized = normalize(question);
        boolean asksReport = contains(normalized, "reporte", "informe");
        boolean asksScan = contains(normalized, "scan", "analisis", "escaneo", "dependencia", "paquete");
        boolean asksVulnerability = contains(normalized, "vulnerabilidad", "aviso", "osv", "riesgo", "seguridad", "critica");
        List<Repository> repositories = new ArrayList<>();
        for (UUID repositoryId : scanRepositoryIds) {
            Repository repository = tenants.execute(organizationId, () -> jdbc.query(
                "select id,full_name from repositories where organization_id=? and id=? and is_enabled",
                (row, index) -> new Repository(row.getObject(1, UUID.class), row.getString(2)), organizationId, repositoryId))
                .stream().findFirst().orElse(null);
            if (repository != null) repositories.add(repository);
        }
        repositories.sort(Comparator.comparingInt((Repository item) -> relevance(normalized, item.name())).reversed());
        if (!repositories.isEmpty() && relevance(normalized, repositories.getFirst().name()) >= 2)
            repositories.removeIf(item -> relevance(normalized, item.name()) < 2);
        if (repositories.size() > 3) repositories = repositories.subList(0, 3);
        List<Evidence> result = new ArrayList<>();
        for (Repository repository : repositories) {
            if (result.size() >= limit) break;
            var inventory = assessments.latest(organizationId, repository.id(), Instant.now(), Duration.ofDays(1));
            String name = repository.name();
            String url = "?view=vulnerabilities&repositoryId=" + repository.id();
            if (asksVulnerability || !asksReport && !asksScan) {
                List<VersionAssessment> affected = inventory.assessments().stream()
                    .filter(item -> !item.advisories().isEmpty())
                    .sorted(Comparator.comparingInt((VersionAssessment item) -> item.advisories().stream()
                        .mapToInt(advisory -> severityRank(advisory.severity())).max().orElse(0)).reversed()).toList();
                StringBuilder summary = new StringBuilder("Repositorio: " + name + ". Evaluación OSV del último análisis: "
                    + inventory.affectedVersions() + " versiones afectadas, " + inventory.advisoryCount() + " avisos. ");
                for (VersionAssessment item : affected) {
                    for (var advisory : item.advisories()) {
                        if (summary.length() > 4200) break;
                        summary.append("Paquete ").append(item.packageVersion().name()).append(" versión ")
                            .append(item.packageVersion().version()).append(": ").append(advisory.id())
                            .append("; severidad ").append(advisory.severity()).append("; ")
                            .append(advisory.summary()).append(". ");
                        if (advisory.details() != null) summary.append(advisory.details(), 0, Math.min(450, advisory.details().length())).append(". ");
                    }
                }
                if (!affected.isEmpty() || asksVulnerability) result.add(evidence("OSV", repository, "Avisos OSV", summary.toString(), url));
            }
            if (asksScan && result.size() < limit) {
                String scan = tenants.execute(organizationId, () -> jdbc.query("""
                    select coalesce(display_name,id::text),status,finished_at,manifest_count,unresolved_count,coverage_status
                    from scans where organization_id=? and repository_id=? order by requested_at desc,id desc limit 1
                    """, (row, index) -> "Análisis " + row.getString(1) + "; estado " + row.getString(2)
                        + "; finalizado " + row.getString(3) + "; manifiestos " + row.getObject(4)
                        + "; dependencias sin resolver " + row.getObject(5) + "; cobertura " + row.getString(6),
                    organizationId, repository.id())).stream().findFirst().orElse(null);
                if (scan != null) {
                    var matchingPackages = inventory.assessments().stream()
                        .filter(item -> normalized.contains(normalize(item.packageVersion().name())))
                        .limit(12).toList();
                    var listedPackages = matchingPackages.isEmpty() ? inventory.assessments().stream().limit(12).toList() : matchingPackages;
                    StringBuilder scanContent = new StringBuilder("Repositorio: " + name + ". " + scan
                        + ". Componentes detectados: " + inventory.assessments().size() + ". ");
                    for (VersionAssessment item : listedPackages)
                        scanContent.append(item.packageVersion().ecosystem()).append(' ')
                            .append(item.packageVersion().name()).append('@').append(item.packageVersion().version()).append("; ");
                    result.add(evidence("SCAN", repository, "Último análisis", scanContent.toString(),
                        "?view=analysis&repositoryId=" + repository.id()));
                }
            }
            if (asksReport && reportRepositoryIds.contains(repository.id()) && result.size() < limit) {
                long relevant = inventory.assessments().stream().flatMap(item -> item.advisories().stream())
                    .filter(item -> contains(normalize(item.severity()), "medium", "high", "critical", "media", "alta", "critica"))
                    .map(item -> item.id()).distinct().count();
                result.add(evidence("REPORT", repository, "Resumen para reportes",
                    "Repositorio: " + name + ". La vista de reportes toma las evaluaciones OSV del último análisis. "
                        + relevant + " avisos de severidad media, alta o crítica disponibles para el informe. "
                        + "No hay un informe persistido en esta fuente; consulta la vista de reportes para generarlo.",
                    "?view=reports&repositoryId=" + repository.id()));
            }
        }
        return result.stream().limit(limit).toList();
    }

    private static Evidence evidence(String type, Repository repository, String label, String content, String url) {
        return new Evidence(UUID.randomUUID(), null, null, label + " · " + repository.name(), repository.name(),
            null, type, content, 1, type, repository.id(), url);
    }
    private static boolean contains(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }
    private static int severityRank(String severity) {
        return switch (normalize(severity)) {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }
    private static int relevance(String question, String name) {
        String normalizedName = normalize(name);
        int matches = 0;
        for (String token : normalizedName.split("[^a-z0-9]+"))
            if (token.length() > 2 && question.contains(token)) matches++;
        return matches;
    }
    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
    private record Repository(UUID id, String name) { }
}
