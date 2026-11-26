package com.dependencyintelligence.assistant.application.dto;

import java.util.List;
import java.util.UUID;

public final class AssistantModels {
    private AssistantModels() { }

    public record Ask(UUID organizationId, UUID userId, List<UUID> repositoryIds, List<UUID> scanRepositoryIds,
                      List<UUID> reportRepositoryIds, UUID conversationId, String question) {
        public Ask {
            repositoryIds = List.copyOf(repositoryIds);
            scanRepositoryIds = List.copyOf(scanRepositoryIds);
            reportRepositoryIds = List.copyOf(reportRepositoryIds);
            question = question == null ? "" : question.strip();
            if (question.isBlank() || question.length() > 4_000) throw new IllegalArgumentException("Escribe una pregunta de hasta 4.000 caracteres.");
        }
    }
    public record Citation(UUID documentId, UUID versionId, UUID chunkId, String documentName, String filename,
            Integer page, String section, String excerpt, String sourceUrl, int ordinal) { }
    public record Answer(UUID conversationId, UUID messageId, String answer, String proposedRepairPrompt,
            List<Citation> citations, String status, String model) {
        public Answer { citations = List.copyOf(citations); }
    }
    public record Evidence(UUID chunkId, UUID documentId, UUID versionId, String documentName, String filename,
            Integer page, String section, String content, double score, String sourceType, UUID repositoryId, String sourceUrl) {
        public Evidence(UUID chunkId, UUID documentId, UUID versionId, String documentName, String filename,
                        Integer page, String section, String content, double score) {
            this(chunkId, documentId, versionId, documentName, filename, page, section, content, score, "DOCUMENT", null, null);
        }
    }
    public record Generated(String text, String proposedRepairPrompt, String model) { }
}
