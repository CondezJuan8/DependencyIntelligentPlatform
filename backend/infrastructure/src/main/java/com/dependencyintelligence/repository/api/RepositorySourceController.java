package com.dependencyintelligence.repository.api;

import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;
import com.dependencyintelligence.repository.infrastructure.storage.RepositorySourceService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.file.Files;
import java.security.Principal;
import java.util.UUID;

@Controller
public final class RepositorySourceController {
    private final AuthorizedRepositoryActions authorization;
    private final RepositorySourceService sources;

    public RepositorySourceController(AuthorizedRepositoryActions authorization, RepositorySourceService sources) {
        this.authorization = authorization; this.sources = sources;
    }

    @GetMapping("/api/organizations/{organizationId}/repositories/{repositoryId}/source")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable UUID organizationId,
            @PathVariable UUID repositoryId, Principal principal) {
        var location = authorization.execute(principal, organizationId, repositoryId, "repository.read",
            () -> sources.locate(organizationId, repositoryId));
        var snapshot = sources.prepare(location);
        StreamingResponseBody body = output -> {
            try (snapshot; var input = Files.newInputStream(snapshot.archive())) { input.transferTo(output); }
        };
        var response = ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=repository-" + repositoryId + ".zip")
            .header("X-Repository-Source", snapshot.provider())
            .header("X-Source-SHA256", snapshot.sha256());
        if (snapshot.commitSha() != null) response.header("X-Git-Commit", snapshot.commitSha());
        return response.body(body);
    }
}
