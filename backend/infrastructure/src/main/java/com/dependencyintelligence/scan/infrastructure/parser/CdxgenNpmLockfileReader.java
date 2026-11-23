package com.dependencyintelligence.scan.infrastructure.parser;

import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.port.out.CycloneDxGenerator;
import com.dependencyintelligence.scan.application.port.out.NpmLockfileReader;
import java.io.InputStream;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public final class CdxgenNpmLockfileReader implements NpmLockfileReader {
    private final ReconciledManifestScanner scanner;
    private final BoundedNpmLockReader manifests;

    public CdxgenNpmLockfileReader(CycloneDxGenerator generator, CycloneDxManifestMapper mapper,
                                      BoundedNpmLockReader manifests) {
        this.scanner = new ReconciledManifestScanner(generator, mapper);
        this.manifests = manifests;
    }

    @Override public ManifestAnalysis read(InputStream archive) {
        return scanner.read(archive, CycloneDxGenerator.Ecosystem.NPM, manifests::read);
    }
}
