package com.dependencyintelligence.identity.api;

import com.dependencyintelligence.identity.application.dto.PlatformAdmissionDecisionResult;
import com.dependencyintelligence.identity.application.port.in.DecidePlatformAdmission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/admission-decisions")
public class PlatformAdmissionDecisionController {
    private final DecidePlatformAdmission decisions;

    public PlatformAdmissionDecisionController(DecidePlatformAdmission decisions) { this.decisions = decisions; }

    @PostMapping
    public PlatformAdmissionDecisionResult decide(@Valid @RequestBody Request body) {
        return decisions.execute(body.token());
    }

    public record Request(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {
        @Override public String toString() { return "Request[REDACTED]"; }
    }
}
