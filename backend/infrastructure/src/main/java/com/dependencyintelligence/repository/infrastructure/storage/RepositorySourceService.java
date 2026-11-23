package com.dependencyintelligence.repository.infrastructure.storage;

import static com.dependencyintelligence.repository.application.RepositorySourceLimits.MAX_GITHUB_ZIP_BYTES;

import com.dependencyintelligence.integration.application.port.out.GitHubAppGateway;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Provides a bounded ZIP snapshot for local uploads and authorized GitHub repositories. */
@Component
public final class RepositorySourceService {
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;
    private final GitHubAppGateway github;
    private final Path root;

    public RepositorySourceService(JdbcTemplate jdbc, TenantTransactions tenants, GitHubAppGateway github,
            @Value("${app.local-repositories.storage-root:./data/repositories}") String storageRoot) {
        this.jdbc = jdbc; this.tenants = tenants; this.github = github;
        this.root = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public SourceLocation locate(UUID organization, UUID repository) {
        return tenants.execute(organization, () -> jdbc.query("""
            select r.provider, r.external_id, r.full_name, s.storage_key, s.sha256,
                   g.installation_external_id
              from repositories r
              left join local_repository_sources s on s.organization_id=r.organization_id and s.repository_id=r.id
              left join github_installations g on g.organization_id=r.organization_id and g.integration_id=r.integration_id
              left join integrations i on i.organization_id=g.organization_id and i.id=g.integration_id
             where r.organization_id=? and r.id=? and r.is_enabled
               and (r.provider='local' or (r.provider='github' and i.status='ACTIVE'))
            """, (row, index) -> new SourceLocation(row.getString(1), row.getString(2), row.getString(3),
                row.getString(4), row.getString(5), row.getObject(6, Long.class)), organization, repository)
            .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Repositorio no disponible.")));
    }

    public SourceSnapshot prepare(SourceLocation location) {
        try {
            if ("local".equals(location.provider())) {
                if (location.storageKey() == null || !location.storageKey().matches("[0-9a-f-]{36}/[0-9a-f-]{36}\\.zip"))
                    throw new IllegalStateException("La fuente local no está disponible.");
                Path archive = root.resolve(location.storageKey()).normalize();
                if (!archive.startsWith(root) || !Files.isRegularFile(archive) || Files.isSymbolicLink(archive)
                    || !isZip(archive) || !sha256(archive).equals(location.sha256()))
                    throw new IllegalStateException("La fuente local no supera la comprobación de integridad.");
                return new SourceSnapshot(archive, "local", location.sha256(), null, false);
            }
            if (!"github".equals(location.provider()) || location.installationId() == null)
                throw new IllegalStateException("La instalación de GitHub ya no está conectada.");
            Path archive = Files.createTempFile("di-github-source-", ".zip");
            try {
                String commit = github.downloadArchive(location.installationId(), location.externalId(), location.fullName(), archive);
                if (Files.size(archive) < 4 || Files.size(archive) > MAX_GITHUB_ZIP_BYTES || !isZip(archive))
                    throw new IllegalStateException("El ZIP de GitHub no cumple el límite de tamaño.");
                return new SourceSnapshot(archive, "github", sha256(archive), commit, true);
            } catch (Exception failure) {
                Files.deleteIfExists(archive);
                throw failure;
            }
        } catch (IOException failure) { throw new IllegalStateException("No se pudo preparar el código fuente.", failure); }
    }

    private static String sha256(Path archive) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(archive)) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }

    private static boolean isZip(Path archive) throws IOException {
        try (var input = Files.newInputStream(archive)) {
            byte[] magic = input.readNBytes(4);
            return magic.length == 4 && magic[0] == 'P' && magic[1] == 'K'
                && magic[2] == 3 && magic[3] == 4;
        }
    }

    public record SourceLocation(String provider, String externalId, String fullName, String storageKey,
                                 String sha256, Long installationId) { }
    public record SourceSnapshot(Path archive, String provider, String sha256, String commitSha, boolean temporary)
            implements AutoCloseable {
        @Override public void close() throws IOException { if (temporary) Files.deleteIfExists(archive); }
    }
}
