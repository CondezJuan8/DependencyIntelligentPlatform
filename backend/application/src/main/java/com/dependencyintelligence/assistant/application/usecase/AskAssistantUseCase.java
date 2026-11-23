package com.dependencyintelligence.assistant.application.usecase;

import com.dependencyintelligence.assistant.application.dto.AssistantModels.*;
import com.dependencyintelligence.assistant.application.port.in.AskAssistant;
import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.*;
import java.util.List;
import java.util.UUID;

/** Coordinates retrieval and generation; providers and persistence remain outside Application. */
public final class AskAssistantUseCase implements AskAssistant {
    private static final String NO_EVIDENCE = "No encontré evidencia documental autorizada que permita responder esta pregunta. Asocia documentación pertinente o formula una pregunta más concreta.";
    private final Embeddings embeddings;
    private final EvidenceSearch search;
    private final AnswerGeneration generation;
    private final ConversationStore conversations;

    public AskAssistantUseCase(Embeddings embeddings, EvidenceSearch search, AnswerGeneration generation, ConversationStore conversations) {
        this.embeddings = embeddings; this.search = search; this.generation = generation; this.conversations = conversations;
    }

    @Override public Answer execute(Ask request) {
        UUID conversation = request.conversationId();
        if (conversation == null) conversation = conversations.createConversation(request.organizationId(), request.userId(), title(request.question()));
        else if (!conversations.canAccess(request.organizationId(), request.userId(), conversation))
            throw new IllegalArgumentException("La conversación no existe o no tienes acceso a ella.");

        int userOrdinal = conversations.nextOrdinal(request.organizationId(), conversation);
        conversations.saveMessage(request.organizationId(), conversation, userOrdinal, "USER", request.question(), null);
        if (request.repositoryIds().isEmpty()) {
            UUID message = conversations.saveMessage(request.organizationId(), conversation, userOrdinal + 1, "ASSISTANT", NO_EVIDENCE, null);
            return new Answer(conversation, message, NO_EVIDENCE, List.of(), "NO_EVIDENCE", null);
        }
        Embedding embedding = embeddings.embed(request.question());
        List<Evidence> evidence = search.search(request.organizationId(), request.repositoryIds(), embedding.model(), embedding.revision(), embedding.values(), 6);
        if (evidence.isEmpty()) {
            UUID message = conversations.saveMessage(request.organizationId(), conversation, userOrdinal + 1, "ASSISTANT", NO_EVIDENCE, null);
            return new Answer(conversation, message, NO_EVIDENCE, List.of(), "NO_EVIDENCE", null);
        }
        Generated generated = generation.answer(request.question(), evidence);
        if (generated.text() == null || generated.text().isBlank()) throw new IllegalStateException("Gemini no devolvió una respuesta utilizable.");
        UUID message = conversations.saveMessage(request.organizationId(), conversation, userOrdinal + 1, "ASSISTANT", generated.text(), generated.model());
        conversations.saveEvidence(request.organizationId(), message, evidence);
        List<Citation> citations = evidence.stream().map(item -> new Citation(item.documentId(), item.chunkId(), item.documentName(),
            item.filename(), item.page(), item.section(), excerpt(item.content()))).toList();
        return new Answer(conversation, message, generated.text(), citations, "ANSWERED", generated.model());
    }

    private static String title(String question) { return question.length() <= 100 ? question : question.substring(0, 97) + "…"; }
    private static String excerpt(String content) { return content.length() <= 420 ? content : content.substring(0, 417) + "…"; }
}
