package com.dependencyintelligence.assistant.application.usecase;

import com.dependencyintelligence.assistant.application.port.in.IndexDocuments;
import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.*;
import java.util.List;
import java.util.UUID;

/** Bounded document indexing; provider calls happen outside a database transaction. */
public final class IndexDocumentsUseCase implements IndexDocuments {
    private final Embeddings embeddings;
    private final IndexableChunks chunks;
    public IndexDocumentsUseCase(Embeddings embeddings, IndexableChunks chunks) { this.embeddings = embeddings; this.chunks = chunks; }
    @Override public IndexResult execute(UUID organizationId, List<UUID> repositoryIds) {
        Embedding probe = embeddings.embed("verificación de dimensión de embedding");
        List<Chunk> pending = chunks.pending(organizationId, repositoryIds, probe.model(), probe.revision());
        int indexed = 0, failed = 0;
        for (int start = 0; start < pending.size(); start += 16) {
            List<Chunk> batch = pending.subList(start, Math.min(start + 16, pending.size()));
            List<Embedding> vectors;
            try {
                vectors = embeddings.embedBatch(batch.stream().map(Chunk::content).toList());
                if (vectors.size() != batch.size()) throw new IllegalStateException("Ollama devolvió un número distinto de vectores.");
            } catch (RuntimeException failure) { failed += batch.size(); continue; }
            for (int i = 0; i < batch.size(); i++) {
                try {
                    Embedding embedding = vectors.get(i);
                    if (!embedding.model().equals(probe.model()) || !embedding.revision().equals(probe.revision()) || embedding.values().length != probe.values().length)
                        throw new IllegalStateException("Ollama cambió el modelo o la dimensión mientras indexaba.");
                    chunks.save(organizationId, batch.get(i), embedding);
                    indexed++;
                } catch (RuntimeException failure) { failed++; }
            }
        }
        return new IndexResult(indexed, failed, chunks.hasPending(organizationId, repositoryIds, probe.model(), probe.revision()));
    }
}
