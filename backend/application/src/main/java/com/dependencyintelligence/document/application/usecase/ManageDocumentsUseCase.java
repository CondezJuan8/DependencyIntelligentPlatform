package com.dependencyintelligence.document.application.usecase;

import com.dependencyintelligence.document.application.dto.DocumentModels.*;
import com.dependencyintelligence.document.application.port.in.ManageDocuments;
import com.dependencyintelligence.document.application.port.out.DocumentLibrary;
import com.dependencyintelligence.document.domain.DocumentName;
import com.dependencyintelligence.document.domain.DocumentType;
import java.util.List;
import java.util.UUID;

public final class ManageDocumentsUseCase implements ManageDocuments {
    private static final int MAX_UPLOAD_BYTES = 50 * 1024 * 1024;
    private final DocumentLibrary documents;
    public ManageDocumentsUseCase(DocumentLibrary documents) { this.documents = documents; }
    @Override public List<Summary> list(UUID organizationId, List<UUID> visibleRepositoryIds) { return documents.list(organizationId, visibleRepositoryIds); }
    @Override public List<Version> versions(UUID organizationId, UUID documentId) { return documents.versions(organizationId, documentId); }
    @Override public Summary upload(Upload upload) { validate(upload); return documents.upload(upload, true, null); }
    @Override public Summary addVersion(Upload upload, UUID documentId) { validate(upload); return documents.upload(upload, false, documentId); }
    @Override public Download download(UUID organizationId, UUID documentId, UUID versionId) { return documents.download(organizationId, documentId, versionId); }
    @Override public List<SearchHit> search(UUID organizationId, List<UUID> visibleRepositoryIds, String query) {
        if (query == null || query.isBlank() || query.strip().length() > 200) throw new IllegalArgumentException("Escribe una búsqueda de hasta 200 caracteres.");
        return documents.search(organizationId, visibleRepositoryIds, query.strip());
    }
    @Override public void delete(UUID organizationId, UUID documentId) { documents.delete(organizationId, documentId); }

    private static void validate(Upload upload) {
        new DocumentName(upload.name());
        DocumentType.fromFilename(upload.originalFilename());
        if (upload.content().length == 0) throw new IllegalArgumentException("El archivo está vacío.");
        if (upload.content().length > MAX_UPLOAD_BYTES) throw new IllegalArgumentException("El límite por documento es 50 MiB.");
        if (upload.repositoryIds().size() > 20) throw new IllegalArgumentException("Puedes asociar el documento a un máximo de 20 repositorios.");
    }
}
