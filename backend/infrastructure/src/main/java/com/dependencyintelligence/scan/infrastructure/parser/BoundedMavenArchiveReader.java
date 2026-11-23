package com.dependencyintelligence.scan.infrastructure.parser;

import static com.dependencyintelligence.repository.application.RepositorySourceLimits.*;

import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.port.out.MavenManifestReader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

/** Extracts only bounded POM bytes; the resolver never runs Maven or project code. */
@Component
public final class BoundedMavenArchiveReader implements MavenManifestReader {
    private static final int MAX_MANIFESTS = 64;
    private static final int MAX_POM_BYTES = 1024 * 1024;
    private final MavenPomDependencyResolver resolver = new MavenPomDependencyResolver();

    @Override public ManifestAnalysis read(InputStream archive) {
        var manifests = new java.util.LinkedHashMap<String, byte[]>();
        var paths = new HashSet<String>();
        int entries = 0;
        int files = 0;
        long uncompressed = 0;
        byte[] buffer = new byte[16 * 1024];
        try (var zip = new ZipInputStream(archive)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > MAX_ARCHIVE_ENTRIES || (!entry.isDirectory() && ++files > MAX_FILES))
                    throw invalid("El ZIP supera 50.000 archivos o 100.000 entradas totales para el análisis Maven.");
                String path = entry.getName();
                if (path == null || path.length() > MAX_PATH_LENGTH || path.startsWith("/") || path.contains("\\")
                    || path.indexOf('\0') >= 0 || List.of(path.split("/", -1)).contains(".."))
                    throw invalid("El ZIP contiene una ruta no permitida.");
                if (!paths.add(path)) throw invalid("El ZIP contiene rutas duplicadas.");
                boolean pom = !entry.isDirectory() && (path.equals("pom.xml") || path.endsWith("/pom.xml"));
                if (pom && manifests.size() >= MAX_MANIFESTS) throw invalid("Demasiados archivos pom.xml.");
                var pomBytes = pom ? new ByteArrayOutputStream() : null;
                for (int count; (count = zip.read(buffer)) != -1;) {
                    uncompressed += count;
                    if (uncompressed > MAX_EXPANDED_BYTES) throw invalid("El ZIP supera 512 MiB descomprimidos.");
                    if (pom) {
                        if (pomBytes.size() + count > MAX_POM_BYTES) throw invalid("Un pom.xml supera 1 MiB.");
                        pomBytes.write(buffer, 0, count);
                    }
                }
                if (pom) manifests.put(path, pomBytes.toByteArray());
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo leer el ZIP del repositorio.", exception);
        }
        return resolver.resolve(Map.copyOf(manifests));
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
