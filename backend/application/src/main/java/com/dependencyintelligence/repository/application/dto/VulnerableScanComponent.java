package com.dependencyintelligence.repository.application.dto;

import java.util.UUID;

/** Package, source and OSV facts read from the exact scan/component relationship. */
public record VulnerableScanComponent(UUID scanId, UUID scanComponentId, String sourceSha256,
        String sourceCommit, String sourceProvider, String osvStatus, String ecosystem,
        String namespace, String packageName, String packageVersion, String manifestPath) { }
