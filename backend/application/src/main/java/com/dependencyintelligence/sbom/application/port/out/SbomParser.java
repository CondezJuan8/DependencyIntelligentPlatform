package com.dependencyintelligence.sbom.application.port.out;

import com.dependencyintelligence.sbom.application.dto.ParsedSbom;
import java.io.InputStream;

public interface SbomParser {
    /** Reads a bounded document without closing the caller-owned input stream. */
    ParsedSbom parse(InputStream content);
}
