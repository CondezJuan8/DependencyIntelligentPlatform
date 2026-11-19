package com.dependencyintelligence.scan.application.port.out;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface RepositoryAnalysisSource {
    Archive open(UUID organizationId, UUID repositoryId);

    interface Archive extends AutoCloseable {
        InputStream content();
        String provider();
        String sha256();
        String commitSha();
        @Override void close() throws IOException;
    }
}
