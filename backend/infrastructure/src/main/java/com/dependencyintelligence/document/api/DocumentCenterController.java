package com.dependencyintelligence.document.api;

import com.dependencyintelligence.document.application.dto.DocumentModels.*;
import com.dependencyintelligence.document.application.dto.DocumentModels;
import com.dependencyintelligence.document.application.port.in.ManageDocuments;
import com.dependencyintelligence.identity.application.port.in.FindAccountWorkspace;
import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/organizations/{organizationId}/documents")
public final class DocumentCenterController {
    private static final long MAX_DOCUMENT_BYTES = 50L * 1024 * 1024;
    private final ManageDocuments documents;
    private final FindAccountWorkspace workspace;
    private final AuthorizedRepositoryActions access;

    public DocumentCenterController(ManageDocuments documents, FindAccountWorkspace workspace, AuthorizedRepositoryActions access) {
        this.documents = documents; this.workspace = workspace; this.access = access;
    }

    @GetMapping
    public List<Summary> list(@PathVariable UUID organizationId, Principal principal) {
        UUID user = UUID.fromString(principal.getName());
        var repositories = visibleRepositories(user, organizationId);
        return access.execute(principal, organizationId, null, "document.read", () -> documents.list(organizationId, repositories));
    }

    @GetMapping("/search")
    public List<SearchHit> search(@PathVariable UUID organizationId, @RequestParam String query, Principal principal) {
        UUID user = UUID.fromString(principal.getName());
        var repositories = visibleRepositories(user, organizationId);
        return access.execute(principal, organizationId, null, "document.read", () -> documents.search(organizationId, repositories, query));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Summary upload(@PathVariable UUID organizationId, @RequestParam String name,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam MultipartFile file, @RequestParam(required = false) List<UUID> repositoryIds, Principal principal) {
        UUID user = UUID.fromString(principal.getName());
        List<UUID> selected = repositoryIds == null ? List.of() : List.copyOf(repositoryIds);
        var allowed = visibleRepositories(user, organizationId);
        if (!allowed.containsAll(selected)) throw new IllegalArgumentException("Solo puedes asociar documentos a repositorios que tienes autorizados.");
        return access.execute(principal, organizationId, null, "document.manage", () -> {
            Summary created = documents.upload(upload(organizationId, user, name, description, file, selected));
            return visibleSummary(organizationId, created.id(), allowed);
        });
    }

    @GetMapping("/{documentId}/versions")
    public List<Version> versions(@PathVariable UUID organizationId, @PathVariable UUID documentId, Principal principal) {
        UUID user = UUID.fromString(principal.getName());
        var visible = visibleRepositories(user, organizationId);
        return access.execute(principal, organizationId, null, "document.read", () -> {
            requireVisibleDocument(organizationId, documentId, visible);
            return documents.versions(organizationId, documentId);
        });
    }

    @PostMapping(value = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Summary addVersion(@PathVariable UUID organizationId, @PathVariable UUID documentId,
            @RequestParam MultipartFile file, Principal principal) {
        UUID user = UUID.fromString(principal.getName());
        var visible = visibleRepositories(user, organizationId);
        return access.execute(principal, organizationId, null, "document.manage", () -> {
            requireVisibleDocument(organizationId, documentId, visible);
            documents.addVersion(upload(organizationId, user, "version", "", file, List.of()), documentId);
            return visibleSummary(organizationId, documentId, visible);
        });
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID organizationId, @PathVariable UUID documentId,
            @RequestParam(required = false) UUID versionId, Principal principal) {
        UUID user = UUID.fromString(principal.getName());
        var visible = visibleRepositories(user, organizationId);
        Download file = access.execute(principal, organizationId, null, "document.read", () -> {
            requireVisibleDocument(organizationId, documentId, visible);
            return documents.download(organizationId, documentId, versionId);
        });
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
            .body(file.content());
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId, @PathVariable UUID documentId, Principal principal) {
        UUID user = UUID.fromString(principal.getName());
        var visible = visibleRepositories(user, organizationId);
        access.execute(principal, organizationId, null, "document.manage", () -> {
            requireVisibleDocument(organizationId, documentId, visible);
            documents.delete(organizationId, documentId);
            return Boolean.TRUE;
        });
        return ResponseEntity.noContent().build();
    }

    private DocumentModels.Upload upload(UUID org, UUID user, String name, String description, MultipartFile file, List<UUID> repositories) {
        if (file.getSize() > MAX_DOCUMENT_BYTES) throw new IllegalArgumentException("El lÃ­mite por documento es 50 MiB.");
        try {
            return new DocumentModels.Upload(org, user, name, description, file.getOriginalFilename(), file.getContentType(), file.getBytes(), repositories);
        } catch (java.io.IOException failure) { throw new IllegalArgumentException("No se pudo leer el archivo que seleccionaste.", failure); }
    }

    private List<UUID> visibleRepositories(UUID user, UUID organizationId) {
        return workspace.repositories(user).stream().filter(repository -> repository.organizationId().equals(organizationId))
            .map(FindAccountWorkspace.Repository::id).toList();
    }

    private void requireVisibleDocument(UUID organizationId, UUID documentId, List<UUID> visibleRepositories) {
        if (documents.list(organizationId, visibleRepositories).stream().noneMatch(document -> document.id().equals(documentId)))
            throw new IllegalArgumentException("No encontramos el documento en tu espacio o no tienes acceso a él.");
    }

    private Summary visibleSummary(UUID organizationId, UUID documentId, List<UUID> visibleRepositories) {
        return documents.list(organizationId, visibleRepositories).stream().filter(document -> document.id().equals(documentId))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("No encontramos el documento en tu espacio o no tienes acceso a él."));
    }
}
