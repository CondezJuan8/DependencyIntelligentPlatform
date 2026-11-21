package com.dependencyintelligence.scan.infrastructure.parser;

import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.port.out.CycloneDxGenerator;
import com.dependencyintelligence.scan.application.port.out.NpmLockfileReader;
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
public final class CdxgenNpmLockfileReader implements NpmLockfileReader {
    private static final long MAX_ARCHIVE_BYTES = 100L * 1024 * 1024;
    private final CycloneDxGenerator generator;
    private final CycloneDxManifestMapper mapper;
    private final NpmLockfileReader lockfileFallback;

    public CdxgenNpmLockfileReader(CycloneDxGenerator generator, CycloneDxManifestMapper mapper,
                                   BoundedNpmLockReader lockfileFallback) {
        this.generator = generator;
        this.mapper = mapper;
        this.lockfileFallback = lockfileFallback;
    }

    @Override public ManifestAnalysis read(InputStream archive) {
        Path replay = null;
        try {
            replay = Files.createTempFile("dependency-intelligence-npm-", ".zip");
            copyBounded(archive, replay);
            ManifestAnalysis fromSbom;
            try (var input = Files.newInputStream(replay)) {
                fromSbom = mapper.map(generator.generate(input, CycloneDxGenerator.Ecosystem.NPM), "NPM");
            }
            if (!fromSbom.dependencies().isEmpty() || fromSbom.manifestCount() == 0) return fromSbom;

            ManifestAnalysis fromLockfile;
            try (var input = Files.newInputStream(replay)) {
                fromLockfile = lockfileFallback.read(input);
            }
            if (fromLockfile.dependencies().isEmpty())
                throw new IllegalArgumentException("cdxgen detectó package-lock.json, pero no produjo paquetes y el lector del lockfile tampoco pudo resolverlos.");

            return new ManifestAnalysis(fromLockfile.dependencies(), fromLockfile.edges(), fromSbom.manifestCount(),
                fromLockfile.unresolvedCount(), "PARTIAL",
                "cdxgen no devolvió componentes; el inventario se recuperó directamente de package-lock.json. Revisa los vínculos y la cobertura parcial.");
        } catch (IOException failure) {
            throw new IllegalArgumentException("No se pudo preparar el lockfile npm para análisis seguro.", failure);
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
                    throw new IllegalArgumentException("El archivo del repositorio supera 100 MiB para el análisis npm.");
                output.write(buffer, 0, read);
            }
        }
    }
}
