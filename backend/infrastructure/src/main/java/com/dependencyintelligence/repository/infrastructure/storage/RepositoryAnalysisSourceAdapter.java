package com.dependencyintelligence.repository.infrastructure.storage;

import com.dependencyintelligence.scan.application.port.out.RepositoryAnalysisSource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;

@Component
public final class RepositoryAnalysisSourceAdapter implements RepositoryAnalysisSource {
    private final RepositorySourceService sources;
    public RepositoryAnalysisSourceAdapter(RepositorySourceService sources) { this.sources = sources; }
    @Override public Archive open(UUID organizationId, UUID repositoryId) {
        var snapshot = sources.prepare(sources.locate(organizationId, repositoryId));
        return new Archive() {
            public InputStream content() throws IOException { return Files.newInputStream(snapshot.archive()); }
            public String provider() { return snapshot.provider(); }
            public String sha256() { return snapshot.sha256(); }
            public String commitSha() { return snapshot.commitSha(); }
            public void close() throws IOException { snapshot.close(); }
        };
    }
}
