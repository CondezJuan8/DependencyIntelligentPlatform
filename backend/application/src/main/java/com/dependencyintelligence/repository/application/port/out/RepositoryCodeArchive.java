package com.dependencyintelligence.repository.application.port.out;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface RepositoryCodeArchive {
    Snapshot open(UUID organizationId, UUID repositoryId);

    interface Snapshot extends AutoCloseable {
        InputStream content();
        String provider();
        String sha256();
        String commitSha();
        @Override void close() throws IOException;
    }
}
