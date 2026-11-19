package com.dependencyintelligence.repository.application.port.out;

import com.dependencyintelligence.repository.application.dto.LocalFolderImportCommand.ProjectFile;
import com.dependencyintelligence.repository.application.dto.LocalRepositorySummary;
import java.util.List;
import java.util.UUID;

public interface LocalRepositorySourceStore {
    StoredSource store(UUID organizationId, UUID repositoryId, UUID uploadedByUser, List<ProjectFile> files);
    List<LocalRepositorySummary> list(UUID organizationId);

    record StoredSource(int fileCount, long sizeBytes) { }
}
