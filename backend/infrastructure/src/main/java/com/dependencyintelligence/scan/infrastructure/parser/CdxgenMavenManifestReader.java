package com.dependencyintelligence.scan.infrastructure.parser;

import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.port.out.CycloneDxGenerator;
import com.dependencyintelligence.scan.application.port.out.MavenManifestReader;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
@Primary
public final class CdxgenMavenManifestReader implements MavenManifestReader {
    private static final long MAX_ARCHIVE_BYTES = 100L * 1024 * 1024;
    private final CycloneDxGenerator generator;
    private final CycloneDxManifestMapper mapper;
    private final MavenManifestReader manifestFallback;

    public CdxgenMavenManifestReader(CycloneDxGenerator generator, CycloneDxManifestMapper mapper,
                                    BoundedMavenArchiveReader manifestFallback) {
        this.generator = generator;
        this.mapper = mapper;
        this.manifestFallback = manifestFallback;
    }

    @Override public ManifestAnalysis read(InputStream archive) {
        Path replay = null;
        try {
            replay = Files.createTempFile("dependency-intelligence-maven-", ".zip");
            copyBounded(archive, replay);

            ManifestAnalysis fromSbom;
            try (var input = Files.newInputStream(replay)) {
                fromSbom = mapper.map(generator.generate(input, CycloneDxGenerator.Ecosystem.MAVEN), "MAVEN");
            }
            if (!fromSbom.dependencies().isEmpty() || fromSbom.manifestCount() == 0) return fromSbom;

            ManifestAnalysis fromManifests;
            try (var input = Files.newInputStream(replay)) {
                fromManifests = manifestFallback.read(input);
            }
            String message = fromManifests.dependencies().isEmpty()
                ? "Se detectó un POM, pero cdxgen no devolvió componentes y no fue posible resolver declaraciones Maven con versión exacta."
                : "cdxgen no devolvió componentes; se recuperaron declaraciones Maven con versión exacta desde los POM. Cobertura parcial: no incluye transitivas ni versiones gestionadas que no se pudieron resolver.";
            return new ManifestAnalysis(fromManifests.dependencies(), fromManifests.edges(),
                Math.max(fromSbom.manifestCount(), fromManifests.manifestCount()),
                fromSbom.unresolvedCount() + fromManifests.unresolvedCount(), "PARTIAL", message);
        } catch (IOException failure) {
            throw new IllegalArgumentException("No se pudo preparar el archivo Maven para el análisis seguro.", failure);
        } finally {
            if (replay != null) try { Files.deleteIfExists(replay); } catch (IOException ignored) { }
        }
    }

    private static void copyBounded(InputStream input, Path destination) throws IOException {
        long total = 0;
        try (OutputStream output = Files.newOutputStream(destination, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[16 * 1024];
            for (int read; (read = input.read(buffer)) != -1;) {
                total += read;
                if (total > MAX_ARCHIVE_BYTES)
                    throw new IllegalArgumentException("El archivo del repositorio supera 100 MiB para el análisis Maven.");
                output.write(buffer, 0, read);
            }
        }
    }
}
