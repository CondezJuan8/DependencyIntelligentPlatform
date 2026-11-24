package com.dependencyintelligence.repository.infrastructure.storage;

import com.dependencyintelligence.repository.application.port.out.RepositoryCodeArchive;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;

/** Opens only the source snapshot whose digest and commit belong to the requested scan. */
@Component
public final class RepositoryCodeArchiveAdapter implements RepositoryCodeArchive {
    private static final long MAX_SNAPSHOT_ARCHIVE_BYTES = 300L * 1024 * 1024;
    private final RepositorySourceService sources;
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;

    public RepositoryCodeArchiveAdapter(RepositorySourceService sources, JdbcTemplate jdbc, TenantTransactions tenants) {
        this.sources = sources; this.jdbc = jdbc; this.tenants = tenants;
    }

    @Override public Snapshot open(UUID organizationId, UUID repositoryId, UUID scanId) {
        ScanSource expected = tenants.execute(organizationId, () -> jdbc.query("""
            select source_sha256, git_reference, origin from scans
            where organization_id=? and repository_id=? and id=? and status='COMPLETED'
            """, (row, index) -> new ScanSource(row.getString(1), row.getString(2), row.getString(3)),
            organizationId, repositoryId, scanId).stream().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("El análisis solicitado no existe o no está completo.")));
        if (expected.sha256() == null || !expected.sha256().matches("[a-fA-F0-9]{64}"))
            return unavailable(expected, "El análisis no conservó un SHA-256 verificable.");
        if ("LOCAL_UPLOAD_SCAN".equals(expected.origin()) && expected.commitSha() != null)
            return unavailable(expected, "La referencia del análisis local no corresponde a un commit Git verificable.");
        try {
            var location = sources.locate(organizationId, repositoryId);
            if (!expected.provider().equals(location.provider()))
                return unavailable(expected, "El origen actual del repositorio difiere del análisis.");
            var snapshot = sources.prepare(location, expected.commitSha());
            if (Files.size(snapshot.archive()) > MAX_SNAPSHOT_ARCHIVE_BYTES) {
                snapshot.close();
                return unavailable(expected, "El ZIP excede el límite de 300 MiB para la revisión estática.");
            }
            if (!expected.sha256().equalsIgnoreCase(snapshot.sha256()) || !same(expected.commitSha(), snapshot.commitSha())) {
                snapshot.close();
                return unavailable(expected, "El SHA-256 o commit del código disponible difiere del análisis.");
            }
            return openVerified(snapshot, expected);
        } catch (RuntimeException | IOException failure) {
            return unavailable(expected, "No está disponible el snapshot exacto del análisis.");
        }
    }

    private Snapshot openVerified(RepositorySourceService.SourceSnapshot snapshot, ScanSource expected) throws IOException {
        try {
            InputStream input = Files.newInputStream(snapshot.archive());
            return new Snapshot() {
                @Override public InputStream content() { return input; }
                @Override public String provider() { return snapshot.provider(); }
                @Override public String sha256() { return snapshot.sha256(); }
                @Override public String commitSha() { return snapshot.commitSha(); }
                @Override public String expectedSha256() { return expected.sha256(); }
                @Override public String expectedCommitSha() { return expected.commitSha(); }
                @Override public boolean available() { return true; }
                @Override public String unavailableReason() { return null; }
                @Override public void close() throws IOException { try { input.close(); } finally { snapshot.close(); } }
            };
        } catch (IOException failure) {
            try { snapshot.close(); } catch (IOException suppressed) { failure.addSuppressed(suppressed); }
            throw failure;
        }
    }

    private static Snapshot unavailable(ScanSource expected, String reason) {
        return new Snapshot() {
            @Override public InputStream content() { throw new IllegalStateException(reason); }
            @Override public String provider() { return expected.provider(); }
            @Override public String sha256() { return null; }
            @Override public String commitSha() { return null; }
            @Override public String expectedSha256() { return expected.sha256(); }
            @Override public String expectedCommitSha() { return expected.commitSha(); }
            @Override public boolean available() { return false; }
            @Override public String unavailableReason() { return reason; }
            @Override public void close() { }
        };
    }

    private static boolean same(String left, String right) { return left == null ? right == null : left.equalsIgnoreCase(right); }
    private record ScanSource(String sha256, String commitSha, String origin) {
        String provider() { return "LOCAL_UPLOAD_SCAN".equals(origin) ? "local" : "github"; }
    }
}
