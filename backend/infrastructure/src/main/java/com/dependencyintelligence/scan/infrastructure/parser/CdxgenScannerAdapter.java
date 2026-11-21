package com.dependencyintelligence.scan.infrastructure.parser;

import com.dependencyintelligence.scan.application.port.out.CycloneDxGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;

/** Calls the isolated cdxgen sidecar with a read-only snapshot; the API never starts containers or exposes a Docker socket. */
@Component
public final class CdxgenScannerAdapter implements CycloneDxGenerator {
    private static final int MAX_FILES = 50_000;
    private static final long MAX_EXPANDED_BYTES = 512L * 1024 * 1024;
    private static final long MAX_BOM_BYTES = 25L * 1024 * 1024;
    private final Path workspace;
    private final RestClient scanner;
    private final ObjectMapper json;

    public CdxgenScannerAdapter(ObjectMapper json,
            @Value("${dependency-intelligence.scanner.cdxgen-url:}") String baseUrl,
            @Value("${dependency-intelligence.scanner.workspace:/tmp/dependency-intelligence-scanner}") String workspace) {
        this.json = json;
        this.workspace = Path.of(workspace).toAbsolutePath().normalize();
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMinutes(6));
        this.scanner = baseUrl == null || baseUrl.isBlank() ? null
            : RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override public byte[] generate(InputStream archive, Ecosystem ecosystem) {
        if (scanner == null) throw new IllegalStateException("El scanner cdxgen no está configurado en este entorno.");
        Path snapshot = null;
        try {
            Files.createDirectories(workspace);
            snapshot = Files.createTempDirectory(workspace, "scan-");
            materialize(archive, snapshot);
            makeReadOnly(snapshot);
            byte[] result = scanner.post().uri("/sbom").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("path", snapshot.toString(), "type", ecosystem == Ecosystem.MAVEN ? "java" : "js",
                    "multiProject", "true", "specVersion", "1.6"))
                .retrieve().body(byte[].class);
            if (result == null || result.length == 0 || result.length > MAX_BOM_BYTES)
                throw new IllegalStateException("cdxgen devolvió un SBOM vacío o superior a 25 MiB.");
            return addManifestCount(result, countManifests(snapshot, ecosystem));
        } catch (RestClientException failure) {
            throw new IllegalStateException("No se pudo completar el análisis aislado con cdxgen.", failure);
        } catch (IOException failure) {
            throw new IllegalArgumentException("No se pudo preparar de forma segura la fuente autorizada para cdxgen.", failure);
        } finally {
            if (snapshot != null) remove(snapshot);
        }
    }

    private byte[] addManifestCount(byte[] bom, int count) throws IOException {
        JsonNode root = json.readTree(bom);
        if (!(root instanceof ObjectNode document) || !"CycloneDX".equals(document.path("bomFormat").asText()))
            throw new IllegalStateException("cdxgen no devolvió un documento CycloneDX válido.");
        ObjectNode metadata = document.with("metadata");
        var properties = metadata.withArray("properties");
        properties.addObject().put("name", "dependency-intelligence:source-manifest-count").put("value", count);
        byte[] annotated = json.writeValueAsBytes(document);
        if (annotated.length > MAX_BOM_BYTES) throw new IllegalStateException("El SBOM enriquecido supera 25 MiB.");
        return annotated;
    }

    private static int countManifests(Path root, Ecosystem ecosystem) throws IOException {
        String expected = ecosystem == Ecosystem.MAVEN ? "pom.xml" : "package-lock.json";
        try (var paths = Files.walk(root)) {
            return Math.toIntExact(paths.filter(Files::isRegularFile).filter(path -> expected.equals(path.getFileName().toString())).count());
        }
    }

    private static void materialize(InputStream input, Path root) throws IOException {
        Set<String> paths = new HashSet<>();
        long expanded = 0;
        int count = 0;
        try (var zip = new ZipInputStream(input)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++count > MAX_FILES) throw new IllegalArgumentException("El repositorio supera el límite de 50.000 archivos para análisis.");
                String name = entry.getName();
                if (!safePath(name) || !paths.add(name)) throw new IllegalArgumentException("El archivo ZIP contiene una ruta duplicada o no permitida.");
                Path target = root.resolve(name).normalize();
                if (!target.startsWith(root) || target.equals(root)) throw new IllegalArgumentException("El archivo ZIP contiene una ruta fuera de la fuente autorizada.");
                if (entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        byte[] buffer = new byte[16 * 1024];
                        for (int read; (read = zip.read(buffer)) != -1;) {
                            expanded += read;
                            if (expanded > MAX_EXPANDED_BYTES) throw new IllegalArgumentException("La fuente supera 512 MiB descomprimidos para análisis.");
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static boolean safePath(String name) {
        if (name == null || name.isBlank() || name.length() > 1024 || name.startsWith("/") || name.contains("\\")
                || name.indexOf('\0') >= 0 || name.matches("^[A-Za-z]:.*")) return false;
        for (String part : name.split("/", -1)) if (part.equals("..") || part.equals(".")) return false;
        return true;
    }

    private static void makeReadOnly(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    var current = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
                    current.remove(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
                    current.remove(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE);
                    current.remove(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE);
                    Files.setPosixFilePermissions(path, current);
                } catch (UnsupportedOperationException ignored) { path.toFile().setWritable(false, false); }
            }
        }
    }

    private static void remove(Path root) {
        try (var paths = Files.walk(root)) {
            List<Path> descendants = new ArrayList<>(paths.sorted(Comparator.reverseOrder()).toList());
            for (Path path : descendants) {
                path.toFile().setWritable(true, true);
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) { /* Workspace janitor can remove stale scan folders on next startup. */ }
    }
}
