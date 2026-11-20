package com.dependencyintelligence.repository.infrastructure.storage;

import com.dependencyintelligence.repository.application.port.out.RepositoryCodeArchive;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;

@Component
public final class RepositoryCodeArchiveAdapter implements RepositoryCodeArchive {
    private final RepositorySourceService sources;
    public RepositoryCodeArchiveAdapter(RepositorySourceService sources) { this.sources = sources; }

    @Override public Snapshot open(UUID organizationId, UUID repositoryId) {
        var snapshot = sources.prepare(sources.locate(organizationId, repositoryId));
        try {
            var input = Files.newInputStream(snapshot.archive());
            return new Snapshot() {
                @Override public InputStream content() { return input; }
                @Override public String provider() { return snapshot.provider(); }
                @Override public String sha256() { return snapshot.sha256(); }
                @Override public String commitSha() { return snapshot.commitSha(); }
                @Override public void close() throws IOException { try { input.close(); } finally { snapshot.close(); } }
            };
        } catch (IOException failure) {
            try { snapshot.close(); } catch (IOException suppressed) { failure.addSuppressed(suppressed); }
            throw new IllegalStateException("No se pudo abrir el código fuente del repositorio.", failure);
        }
    }
}
