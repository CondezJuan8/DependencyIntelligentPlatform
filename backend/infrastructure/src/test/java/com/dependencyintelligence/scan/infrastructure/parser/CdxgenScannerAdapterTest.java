package com.dependencyintelligence.scan.infrastructure.parser;

import com.dependencyintelligence.scan.application.port.out.CycloneDxGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CdxgenScannerAdapterTest {
    @Test void sendsOnlyAReadOnlyExtractedSnapshotAndFixedScannerOptions() throws Exception {
        AtomicReference<String> scannedPath = new AtomicReference<>();
        AtomicReference<String> scannerType = new AtomicReference<>();
        AtomicBoolean sourcePresentDuringRequest = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sbom", exchange -> {
            var body = new ObjectMapper().readTree(exchange.getRequestBody());
            scannedPath.set(body.path("path").asText());
            scannerType.set(body.path("type").asText());
            sourcePresentDuringRequest.set(Files.isRegularFile(Path.of(scannedPath.get()).resolve("package-lock.json")));
            byte[] response = "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.6\",\"components\":[],\"dependencies\":[]}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        Path workspace = Files.createTempDirectory("cdxgen-test-");
        try {
            var adapter = new CdxgenScannerAdapter(new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort(), workspace.toString());
            byte[] archive = zip("package-lock.json", "{\"lockfileVersion\":3}");
            byte[] sbom = adapter.generate(new ByteArrayInputStream(archive), CycloneDxGenerator.Ecosystem.NPM);

            assertTrue(new String(sbom).contains("CycloneDX"));
            assertEquals("js", scannerType.get());
            assertTrue(sourcePresentDuringRequest.get());
            assertFalse(Files.exists(Path.of(scannedPath.get())));
        } finally {
            server.stop(0);
            try (var paths = Files.walk(workspace)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().setWritable(true, true));
            }
            try (var paths = Files.walk(workspace)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    @Test void rejectsArchiveTraversalBeforeCallingScanner() throws Exception {
        Path workspace = Files.createTempDirectory("cdxgen-path-test-");
        var adapter = new CdxgenScannerAdapter(new ObjectMapper(), "http://127.0.0.1:1", workspace.toString());
        assertThrows(IllegalArgumentException.class, () -> adapter.generate(new ByteArrayInputStream(zip("../outside", "bad")),
            CycloneDxGenerator.Ecosystem.MAVEN));
        assertEquals(0, Files.list(workspace).count());
        Files.deleteIfExists(workspace);
    }

    private static byte[] zip(String name, String content) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
