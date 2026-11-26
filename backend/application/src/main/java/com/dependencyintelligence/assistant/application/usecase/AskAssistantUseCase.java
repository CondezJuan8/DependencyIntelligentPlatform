package com.dependencyintelligence.assistant.application.usecase;

import com.dependencyintelligence.assistant.application.dto.AssistantModels.*;
import com.dependencyintelligence.assistant.application.port.in.AskAssistant;
import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.*;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

/** Coordinates retrieval and generation; providers and persistence remain outside Application. */
public final class AskAssistantUseCase implements AskAssistant {
    private static final String NO_EVIDENCE = "No encontré suficiente información en las fuentes a las que tienes acceso para responder. Puedes aportar más contexto o preguntar por otro repositorio o paquete.";
    private final Embeddings embeddings;
    private final EvidenceSearch search;
    private final WorkspaceEvidenceSearch workspaceSearch;
    private final AnswerGeneration generation;
    private final ConversationStore conversations;

    public AskAssistantUseCase(Embeddings embeddings, EvidenceSearch search, WorkspaceEvidenceSearch workspaceSearch,
                               AnswerGeneration generation, ConversationStore conversations) {
        this.embeddings = embeddings; this.search = search; this.workspaceSearch = workspaceSearch;
        this.generation = generation; this.conversations = conversations;
    }

    @Override public Answer execute(Ask request) {
        UUID conversation = request.conversationId();
        if (conversation == null) conversation = conversations.createConversation(request.organizationId(), request.userId(), title(request.question()));
        else if (!conversations.canAccess(request.organizationId(), request.userId(), conversation))
            throw new IllegalArgumentException("La conversación no existe o no tienes acceso a ella.");

        int userOrdinal = conversations.nextOrdinal(request.organizationId(), conversation);
        conversations.saveMessage(request.organizationId(), conversation, userOrdinal, "USER", request.question(), null);
        List<Evidence> evidence = new ArrayList<>(workspaceSearch.search(request.organizationId(), request.scanRepositoryIds(),
            request.reportRepositoryIds(), request.question(), 6));
        if (evidence.size() < 6) {
            Embedding embedding = embeddings.embed(request.question());
            evidence.addAll(search.search(request.organizationId(), request.repositoryIds(), embedding.model(), embedding.revision(),
                request.question(), embedding.values(), 6 - evidence.size()));
        }
        if (evidence.isEmpty()) {
            UUID message = conversations.saveMessage(request.organizationId(), conversation, userOrdinal + 1, "ASSISTANT", NO_EVIDENCE, null);
            return new Answer(conversation, message, NO_EVIDENCE, null, List.of(), "NO_EVIDENCE", null);
        }
        Generated generated = generation.answer(request.question(), evidence);
        if (generated.text() == null || generated.text().isBlank() || generated.proposedRepairPrompt() == null || generated.proposedRepairPrompt().isBlank())
            throw new IllegalStateException("OpenAI devolvió una respuesta incompleta.");
        String persistedAnswer = generated.text() + "\n\nPrompt de reparación propuesto (requiere revisión humana):\n" + generated.proposedRepairPrompt();
        UUID message = conversations.saveMessage(request.organizationId(), conversation, userOrdinal + 1, "ASSISTANT", persistedAnswer, generated.model());
        conversations.saveEvidence(request.organizationId(), message, evidence);
        List<Citation> citations = java.util.stream.IntStream.range(0, evidence.size()).mapToObj(index -> {
            Evidence item = evidence.get(index);
            return new Citation(item.documentId(), item.versionId(), item.chunkId(), item.documentName(), item.filename(),
                item.page(), item.section(), excerpt(item.content()), item.sourceUrl(), index + 1);
        }).toList();
        return new Answer(conversation, message, generated.text(), generated.proposedRepairPrompt(), citations, "ANSWERED", generated.model());
    }

    private static String title(String question) { return question.length() <= 100 ? question : question.substring(0, 97) + "…"; }
    private static String excerpt(String content) { return content.length() <= 420 ? content : content.substring(0, 417) + "…"; }
}
