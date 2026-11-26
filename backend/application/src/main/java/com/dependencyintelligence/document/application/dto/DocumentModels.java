package com.dependencyintelligence.document.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.dependencyintelligence.document.domain.DocumentPurpose;

public final class DocumentModels {
    private DocumentModels() { }
    public record Upload(UUID organizationId, UUID userId, String name, String description,
            String originalFilename, String clientMimeType, byte[] content, List<UUID> repositoryIds, DocumentPurpose purpose) {
        public Upload { content = content.clone(); repositoryIds = List.copyOf(repositoryIds); }
        @Override public byte[] content() { return content.clone(); }
    }
    public record Summary(UUID id, String name, String type, String description, String status,
            String indexingStatus, int latestVersion, int versionCount, long sizeBytes,
            String mimeType, String sha256, Instant uploadedAt, List<String> repositories,
            DocumentPurpose purpose, UUID contextRepositoryId) {
        public Summary { repositories = List.copyOf(repositories); }
    }
    public record Version(UUID id, int version, String filename, String mimeType, long sizeBytes,
            String sha256, String indexingStatus, Instant uploadedAt) { }
    public record SearchHit(UUID documentId, UUID versionId, String documentName, String filename,
            String excerpt, Integer pageStart, String repositoryName) { }
    public record Download(String filename, String mimeType, byte[] content) {
        public Download { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }
}
