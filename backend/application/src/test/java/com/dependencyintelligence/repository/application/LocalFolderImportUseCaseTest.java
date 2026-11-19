package com.dependencyintelligence.repository.application;

import com.dependencyintelligence.repository.application.dto.LocalFolderImportCommand;
import com.dependencyintelligence.repository.application.port.out.LocalRepositorySourceStore;
import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.application.usecase.ImportLocalFolderUseCase;
import com.dependencyintelligence.repository.domain.Repository;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.shared.domain.OrganizationId;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LocalFolderImportUseCaseTest {
    private final UUID organization = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final MemoryRepositories repositories = new MemoryRepositories();
    private final MemorySource sources = new MemorySource();
    private final ImportLocalFolderUseCase useCase = new ImportLocalFolderUseCase(id -> id.value().equals(organization),
        repositories, sources, Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC));

    @Test void importsValidatedFolderAsLocalRepository() {
        var result = useCase.execute(command("project", "project/pom.xml", 32));
        assertEquals("project", result.name());
        assertEquals(1, result.fileCount());
        assertEquals("local", repositories.saved.provider());
        assertEquals(result.id().toString(), repositories.saved.externalId());
        assertEquals(List.of("pom.xml"), sources.paths);
    }

    @Test void rejectsTraversalBeforeCreatingRepository() {
        var command = new LocalFolderImportCommand(organization, user, "project", List.of(file("project/../secret", 1)));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(command));
        assertNull(repositories.saved);
        assertFalse(sources.called);
    }

    @Test void rejectsGeneratedDirectoriesAndOversizedFolders() {
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(command("project", "project/node_modules/x", 1)));
        assertNull(repositories.saved);
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(command("project", "project/large.bin", 100L * 1024 * 1024 + 1)));
        assertNull(repositories.saved);
        var mixedRoots = new LocalFolderImportCommand(organization, user, "project",
            List.of(file("project/a.txt", 1), file("other/b.txt", 1)));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(mixedRoots));
        assertNull(repositories.saved);
    }

    @Test void rejectsCredentialFilesButAllowsExampleConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(command("project", "project/.env", 1)));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(command("project", "project/secrets/private.pem", 1)));
        assertNull(repositories.saved);
        var result = useCase.execute(command("project", "project/.env.example", 1));
        assertEquals("project", result.name());
    }

    private LocalFolderImportCommand command(String name, String path, long size) {
        return new LocalFolderImportCommand(organization, user, name, List.of(file(path, size)));
    }
    private LocalFolderImportCommand.ProjectFile file(String path, long size) {
        return new LocalFolderImportCommand.ProjectFile(path, size, () -> new ByteArrayInputStream(new byte[]{1}));
    }

    private static final class MemoryRepositories implements RepositoryStore {
        Repository saved;
        @Override public Repository add(Repository repository) { saved = repository; return repository; }
        @Override public Optional<Repository> find(OrganizationId organizationId, RepositoryId repositoryId) {
            return Optional.ofNullable(saved).filter(repository -> repository.organizationId().equals(organizationId)
                && repository.id().equals(repositoryId));
        }
    }
    private static final class MemorySource implements LocalRepositorySourceStore {
        boolean called;
        List<String> paths = List.of();
        @Override public StoredSource store(UUID org, UUID repo, UUID actor, List<LocalFolderImportCommand.ProjectFile> files) {
            called = true; paths = files.stream().map(LocalFolderImportCommand.ProjectFile::relativePath).toList();
            return new StoredSource(files.size(), 99);
        }
        @Override public List<com.dependencyintelligence.repository.application.dto.LocalRepositorySummary> list(UUID org) { return List.of(); }
    }
}
