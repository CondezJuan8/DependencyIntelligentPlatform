package com.dependencyintelligence.assistant.application.dto;

import java.util.List;
import java.util.UUID;

public final class AssistantModels {
    private AssistantModels() { }

    public record Ask(UUID organizationId, UUID userId, List<UUID> repositoryIds, UUID conversationId, String question) {
        public Ask {
            repositoryIds = List.copyOf(repositoryIds);
            question = question == null ? "" : question.strip();
            if (question.isBlank() || question.length() > 4_000) throw new IllegalArgumentException("Escribe una pregunta de hasta 4.000 caracteres.");
        }
    }
    public record Citation(UUID documentId, UUID versionId, UUID chunkId, String documentName, String filename,
            Integer page, String section, String excerpt) { }
    public record Answer(UUID conversationId, UUID messageId, String answer, String proposedRepairPrompt,
            List<Citation> citations, String status, String model) {
        public Answer { citations = List.copyOf(citations); }
    }
    public record Evidence(UUID chunkId, UUID documentId, UUID versionId, String documentName, String filename,
            Integer page, String section, String content, double score) { }
    public record Generated(String text, String proposedRepairPrompt, String model) { }
}
