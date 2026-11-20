package com.dependencyintelligence.repository.application.dto;

import java.util.List;

public record RepositoryCodeEvidence(String advisoryId, String status, String sourceProvider,
        String sourceSha256, String sourceCommit, int scannedFiles, List<Match> matches) {
    public RepositoryCodeEvidence { matches = List.copyOf(matches); }
    public record Match(String filePath, int lineNumber, String excerpt, String reason) { }
}
