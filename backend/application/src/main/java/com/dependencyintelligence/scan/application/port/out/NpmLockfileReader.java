package com.dependencyintelligence.scan.application.port.out;
import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import java.io.InputStream;
public interface NpmLockfileReader { ManifestAnalysis read(InputStream archive); }
