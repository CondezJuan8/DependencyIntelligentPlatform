package com.dependencyintelligence.shared.infrastructure.demo;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.UUID;

@Validated
@ConfigurationProperties(prefix = "demo")
public record DemoProperties(@NotNull UUID organizationId, @NotNull UUID repositoryId) { }
