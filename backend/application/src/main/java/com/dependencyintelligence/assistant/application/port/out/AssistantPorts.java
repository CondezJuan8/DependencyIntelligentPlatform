package com.dependencyintelligence.assistant.application.port.out;

import com.dependencyintelligence.assistant.application.dto.AssistantModels.*;
import java.util.List;
import java.util.UUID;

public final class AssistantPorts {
    private AssistantPorts() { }
    public interface Embeddings {
        Embedding embed(String text);
        default List<Embedding> embedBatch(List<String> texts) { return texts.stream().map(this::embed).toList(); }
    }
    public interface EvidenceSearch { List<Evidence> search(UUID organizationId, List<UUID> repositoryIds, String model, String revision, String query, float[] vector, int limit); }
    public interface WorkspaceEvidenceSearch { List<Evidence> search(UUID organizationId, List<UUID> scanRepositoryIds,
        List<UUID> reportRepositoryIds, String query, int limit); }
    public interface AnswerGeneration { Generated answer(String question, List<Evidence> evidence); }
    public interface ConversationStore {
        record Summary(UUID id, String title, String updatedAt) { }
        record Message(String role, String content, String proposedRepairPrompt, List<Citation> citations) { }
        UUID createConversation(UUID organizationId, UUID userId, String title);
        List<Summary> listConversations(UUID organizationId, UUID userId);
        List<Message> readConversation(UUID organizationId, UUID userId, UUID conversationId, List<UUID> repositoryIds,
                                       List<UUID> scanRepositoryIds, List<UUID> reportRepositoryIds);
        int nextOrdinal(UUID organizationId, UUID conversationId);
        UUID saveMessage(UUID organizationId, UUID conversationId, int ordinal, String role, String content, String model);
        void saveEvidence(UUID organizationId, UUID messageId, List<Evidence> evidence);
        void deleteConversation(UUID organizationId, UUID userId, UUID conversationId);
        boolean canAccess(UUID organizationId, UUID userId, UUID conversationId);
    }
    public interface IndexableChunks {
        List<Chunk> pending(UUID organizationId, List<UUID> repositoryIds, String model, String revision);
        boolean hasPending(UUID organizationId, List<UUID> repositoryIds, String model, String revision);
        void save(UUID organizationId, Chunk chunk, Embedding embedding);
    }
    public record Chunk(UUID id, String content) { }
    public record IndexResult(int indexed, int failed, boolean morePending) { }
    public record Embedding(float[] values, String model, String revision) {
        public Embedding { values = values.clone(); if (values.length == 0) throw new IllegalArgumentException("El proveedor devolvió un embedding vacío."); }
        @Override public float[] values() { return values.clone(); }
    }
}
