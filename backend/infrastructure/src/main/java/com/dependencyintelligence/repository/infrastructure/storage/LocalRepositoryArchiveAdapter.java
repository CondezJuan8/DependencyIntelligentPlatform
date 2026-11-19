package com.dependencyintelligence.repository.infrastructure.storage;

import com.dependencyintelligence.repository.application.dto.LocalFolderImportCommand.ProjectFile;
import com.dependencyintelligence.repository.application.port.out.LocalRepositorySourceStore;
import com.dependencyintelligence.repository.application.dto.LocalRepositorySummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Stores uploaded source archives outside the web root; names from users never form storage paths. */
@Repository
public class LocalRepositoryArchiveAdapter implements LocalRepositorySourceStore {
    private final JdbcTemplate jdbc;
    private final Path root;

    public LocalRepositoryArchiveAdapter(JdbcTemplate jdbc,
            @Value("${app.local-repositories.storage-root:./data/repositories}") String storageRoot) {
        this.jdbc = jdbc; this.root = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Override public StoredSource store(UUID organizationId, UUID repositoryId, UUID uploadedByUser, List<ProjectFile> files) {
        Path target = root.resolve(organizationId.toString()).resolve(repositoryId + ".zip").normalize();
        if (!target.startsWith(root)) throw new IllegalStateException("Invalid private repository storage key.");
        try {
            Files.createDirectories(target.getParent());
            try (var output = new ZipOutputStream(Files.newOutputStream(target))) {
                for (ProjectFile file : files) {
                    output.putNextEntry(new ZipEntry(file.relativePath()));
                    try (InputStream input = file.content().open()) { input.transferTo(output); }
                    output.closeEntry();
                }
            }
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(target)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            long size = Files.size(target);
            String storageKey = organizationId + "/" + repositoryId + ".zip";
            jdbc.update("""
                insert into local_repository_sources(organization_id, repository_id, storage_backend, storage_key,
                  sha256, size_bytes, file_count, uploaded_by_membership_id)
                values (?, ?, 'LOCAL', ?, ?, ?, ?,
                  (select id from organization_memberships where organization_id=? and user_id=? and status='ACTIVE'))
                """, organizationId, repositoryId, storageKey, sha256, size, files.size(), organizationId, uploadedByUser);
            return new StoredSource(files.size(), size);
        } catch (Exception exception) {
            try { Files.deleteIfExists(target); } catch (Exception cleanup) { exception.addSuppressed(cleanup); }
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("No se pudo guardar la carpeta del repositorio en el almacenamiento privado.", exception);
        }
    }

    @Override public List<LocalRepositorySummary> list(UUID organizationId) {
        return jdbc.query("""
            select r.id, r.full_name, s.file_count, s.size_bytes, s.created_at
            from local_repository_sources s
            join repositories r on r.organization_id=s.organization_id and r.id=s.repository_id
            where s.organization_id=? and r.is_enabled
            order by s.created_at desc, r.full_name
            """, (row, index) -> new LocalRepositorySummary(row.getObject(1, UUID.class), row.getString(2),
                row.getInt(3), row.getLong(4), row.getTimestamp(5).toInstant()), organizationId);
    }
}
