package com.dependencyintelligence.repository.infrastructure.storage;

import com.dependencyintelligence.integration.application.port.out.GitHubAppGateway;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepositorySourceServiceTest {
    @TempDir Path root;

    @Test void localSourceKeepsItsArchiveAndRejectsCorruption() throws Exception {
        var gateway = mock(GitHubAppGateway.class);
        var sources = new RepositorySourceService(mock(JdbcTemplate.class), mock(TenantTransactions.class), gateway, root.toString());
        String key = UUID.randomUUID() + "/" + UUID.randomUUID() + ".zip";
        Path file = root.resolve(key); Files.createDirectories(file.getParent()); writeZip(file);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        var location = new RepositorySourceService.SourceLocation("local", null, "fixture", key, hash, null);
        try (var snapshot = sources.prepare(location)) {
            assertEquals(file, snapshot.archive()); assertEquals(hash, snapshot.sha256());
        }
        assertTrue(Files.exists(file));
        Files.writeString(file, "tampered");
        assertThrows(IllegalStateException.class, () -> sources.prepare(location));
        verifyNoInteractions(gateway);
    }

    @Test void githubSourceIsPinnedAndItsTemporaryArchiveIsDeleted() throws Exception {
        var gateway = mock(GitHubAppGateway.class);
        when(gateway.downloadArchive(eq(77L), eq("90210"), eq("CondezJuan8/demo"), any(Path.class)))
            .thenAnswer(invocation -> { writeZip(invocation.getArgument(3)); return "a".repeat(40); });
        var sources = new RepositorySourceService(mock(JdbcTemplate.class), mock(TenantTransactions.class), gateway, root.toString());
        var location = new RepositorySourceService.SourceLocation("github", "90210", "CondezJuan8/demo", null, null, 77L);
        Path temporary;
        try (var snapshot = sources.prepare(location)) {
            temporary = snapshot.archive();
            assertEquals("a".repeat(40), snapshot.commitSha());
            assertTrue(Files.exists(temporary));
        }
        assertFalse(Files.exists(temporary));
    }

    private static void writeZip(Path target) throws Exception {
        try (var output = new ZipOutputStream(Files.newOutputStream(target))) {
            output.putNextEntry(new ZipEntry("pom.xml")); output.write("<project/>".getBytes()); output.closeEntry();
        }
    }
}
