package com.dependencyintelligence.document.application.port.in;

import com.dependencyintelligence.document.application.dto.DocumentModels.*;
import java.util.List;
import java.util.UUID;

public interface ManageDocuments {
    List<Summary> list(UUID organizationId, List<UUID> visibleRepositoryIds);
    List<Version> versions(UUID organizationId, UUID documentId);
    Summary upload(Upload upload);
    Summary addVersion(Upload upload, UUID documentId);
    Download download(UUID organizationId, UUID documentId, UUID versionId);
    List<SearchHit> search(UUID organizationId, List<UUID> visibleRepositoryIds, String query);
    void delete(UUID organizationId, UUID documentId);
}
