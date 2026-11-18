package com.dependencyintelligence.integration.api;

import com.dependencyintelligence.integration.application.port.in.ManageGitHubInstallation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.security.Principal;

@RestController
public class GitHubAppAuthorizationCallbackController {
    private final ManageGitHubInstallation github;
    private final String baseUrl;
    public GitHubAppAuthorizationCallbackController(ManageGitHubInstallation github,
            @Value("${app.identity.public-base-url}") String baseUrl) {
        this.github = github; this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @GetMapping("/api/integrations/github/app-callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         Principal principal, jakarta.servlet.http.HttpServletRequest request) {
        String status = "error";
        var session = request.getSession(false);
        if (error == null && code != null && code.matches("[A-Za-z0-9_-]{1,256}") && state != null && state.matches("[A-Za-z0-9_-]{43}")
                && principal != null && session != null) {
            try {
                github.completeFromCallback(java.util.UUID.fromString(principal.getName()), session.getId(), state, code);
                status = "connected";
            } catch (RuntimeException ignored) {
                status = "error";
            }
        } else if (error != null) status = "cancelled";
        var headers = new HttpHeaders();
        headers.setLocation(URI.create(baseUrl + "/?view=repositories&github_connection=" + status));
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
