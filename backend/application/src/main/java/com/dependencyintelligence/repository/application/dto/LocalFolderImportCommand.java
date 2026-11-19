package com.dependencyintelligence.repository.application.dto;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public record LocalFolderImportCommand(UUID organizationId, UUID userId, String name, List<ProjectFile> files) {
    public LocalFolderImportCommand { files = List.copyOf(files); }

    @FunctionalInterface
    public interface StreamSource { InputStream open() throws IOException; }

    public record ProjectFile(String relativePath, long size, StreamSource content) { }
}
