package com.dependencyintelligence.assistant.api;

import com.dependencyintelligence.assistant.application.dto.AssistantModels.Answer;
import com.dependencyintelligence.assistant.application.dto.AssistantModels.Ask;
import com.dependencyintelligence.assistant.application.port.in.AskAssistant;
import com.dependencyintelligence.assistant.application.port.in.IndexDocuments;
import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.IndexResult;
import com.dependencyintelligence.identity.application.port.in.FindAccountWorkspace;
import com.dependencyintelligence.identity.application.exception.PermissionDeniedException;
import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations/{organizationId}/assistant")
public final class AssistantController {
    public record AskRequest(UUID conversationId, String question, UUID repositoryId) { }
    private final AskAssistant assistant;
    private final IndexDocuments indexing;
    private final FindAccountWorkspace workspace;
    private final AuthorizedRepositoryActions access;
    public AssistantController(AskAssistant assistant, IndexDocuments indexing, FindAccountWorkspace workspace, AuthorizedRepositoryActions access) {
        this.assistant = assistant; this.indexing = indexing; this.workspace = workspace; this.access = access;
    }
    @PostMapping("/ask")
    public Answer ask(@PathVariable UUID organizationId, @RequestBody AskRequest body, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        access.execute(principal, organizationId, null, "assistant.query", () -> Boolean.TRUE);
        List<UUID> repositories = visibleRepositories(principal, userId, organizationId);
        if (body.repositoryId() != null) {
            if (!repositories.contains(body.repositoryId())) throw new PermissionDeniedException();
            repositories = List.of(body.repositoryId());
        }
        return assistant.execute(new Ask(organizationId, userId, repositories, body.conversationId(), body.question()));
    }
    @PostMapping("/index")
    public IndexResult index(@PathVariable UUID organizationId, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        access.execute(principal, organizationId, null, "assistant.query", () -> Boolean.TRUE);
        List<UUID> repositories = visibleRepositories(principal, userId, organizationId);
        return indexing.execute(organizationId, repositories);
    }
    private List<UUID> visibleRepositories(Principal principal, UUID user, UUID organizationId) {
        return workspace.repositories(user).stream().filter(repository -> repository.organizationId().equals(organizationId))
            .map(FindAccountWorkspace.Repository::id).filter(repository -> canReadDocuments(principal, organizationId, repository)).toList();
    }
    private boolean canReadDocuments(Principal principal, UUID organizationId, UUID repositoryId) {
        try { access.execute(principal, organizationId, repositoryId, "document.read", () -> Boolean.TRUE); return true; }
        catch (PermissionDeniedException denied) { return false; }
    }
}
