package com.dependencyintelligence.scan.infrastructure.parser;

import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.port.out.CycloneDxGenerator;
import com.dependencyintelligence.scan.application.port.out.MavenManifestReader;
import java.io.InputStream;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public final class CdxgenMavenManifestReader implements MavenManifestReader {
    private final ReconciledManifestScanner scanner;
    private final BoundedMavenArchiveReader manifests;

    public CdxgenMavenManifestReader(CycloneDxGenerator generator, CycloneDxManifestMapper mapper,
                                      BoundedMavenArchiveReader manifests) {
        this.scanner = new ReconciledManifestScanner(generator, mapper);
        this.manifests = manifests;
    }

    @Override public ManifestAnalysis read(InputStream archive) {
        return scanner.read(archive, CycloneDxGenerator.Ecosystem.MAVEN, manifests::read);
    }
}
