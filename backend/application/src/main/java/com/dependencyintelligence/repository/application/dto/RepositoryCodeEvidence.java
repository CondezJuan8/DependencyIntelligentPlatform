package com.dependencyintelligence.repository.application.dto;

import java.util.List;
import java.util.UUID;

/** Read-only exposure evidence tied to one immutable scan and OSV result. */
public record RepositoryCodeEvidence(UUID scanId, UUID scanComponentId, String advisoryId, String status,
        List<String> states, boolean versionAffected, String osvStatus, String sourceProvider,
        String expectedSourceSha256, String sourceSha256, String sourceCommit,
        String ecosystem, String namespace, String packageName, String packageVersion,
        String manifestPath, String explanation, List<Match> matches, boolean analysisComplete) {
    public RepositoryCodeEvidence { states = List.copyOf(states); matches = List.copyOf(matches); }

    public record Match(String filePath, Integer lineNumber, Integer endLine, String symbol,
            String evidenceType, String excerpt, String reason) { }
}
