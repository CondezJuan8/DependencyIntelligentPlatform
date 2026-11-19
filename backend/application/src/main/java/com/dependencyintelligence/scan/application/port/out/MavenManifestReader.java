package com.dependencyintelligence.scan.application.port.out;

import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import java.io.InputStream;

public interface MavenManifestReader {
    ManifestAnalysis read(InputStream archive);
}
