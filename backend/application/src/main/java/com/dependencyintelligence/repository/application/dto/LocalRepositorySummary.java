package com.dependencyintelligence.repository.application.dto;

import java.time.Instant;
import java.util.UUID;

public record LocalRepositorySummary(UUID id, String name, int fileCount, long sizeBytes, Instant uploadedAt) { }
