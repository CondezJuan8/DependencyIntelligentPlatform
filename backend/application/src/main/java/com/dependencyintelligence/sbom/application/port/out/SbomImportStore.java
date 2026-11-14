package com.dependencyintelligence.sbom.application.port.out;

import com.dependencyintelligence.scan.domain.Scan;
import com.dependencyintelligence.sbom.application.dto.ParsedSbom;

public interface SbomImportStore {
    /** Saves snapshot, provenance, package catalog and occurrences atomically. */
    void save(Scan snapshot, ParsedSbom sbom, String sha256);
}
