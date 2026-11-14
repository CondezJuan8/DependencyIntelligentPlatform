package com.dependencyintelligence.sbom.application.port.in;

import com.dependencyintelligence.sbom.application.dto.ImportSbomCommand;
import com.dependencyintelligence.sbom.application.dto.ImportSbomResult;

public interface ImportSbom {
    ImportSbomResult execute(ImportSbomCommand command);
}
