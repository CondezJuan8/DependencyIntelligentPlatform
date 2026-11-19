package com.dependencyintelligence.repository.application.dto;

import java.util.UUID;

public record LocalFolderImportResult(UUID id, String name, int fileCount, long sizeBytes) { }
