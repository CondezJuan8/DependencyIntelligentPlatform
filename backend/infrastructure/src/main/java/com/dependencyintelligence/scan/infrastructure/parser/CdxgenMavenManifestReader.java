package com.dependencyintelligence.scan.infrastructure.parser;

import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.port.out.CycloneDxGenerator;
import com.dependencyintelligence.scan.application.port.out.MavenManifestReader;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@Primary
public final class CdxgenMavenManifestReader implements MavenManifestReader {
    private final CycloneDxGenerator generator;
    private final CycloneDxManifestMapper mapper;

    public CdxgenMavenManifestReader(CycloneDxGenerator generator, CycloneDxManifestMapper mapper) {
        this.generator = generator;
        this.mapper = mapper;
    }

    @Override public ManifestAnalysis read(InputStream archive) {
        return mapper.map(generator.generate(archive, CycloneDxGenerator.Ecosystem.MAVEN), "MAVEN");
    }
}
