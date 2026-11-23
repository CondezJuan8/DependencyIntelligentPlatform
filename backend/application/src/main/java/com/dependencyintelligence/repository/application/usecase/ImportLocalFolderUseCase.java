package com.dependencyintelligence.repository.application.usecase;

import static com.dependencyintelligence.repository.application.RepositorySourceLimits.*;

import com.dependencyintelligence.organization.application.port.out.OrganizationLookup;
import com.dependencyintelligence.repository.application.dto.LocalFolderImportCommand;
import com.dependencyintelligence.repository.application.dto.LocalFolderImportResult;
import com.dependencyintelligence.repository.application.port.in.ImportLocalFolder;
import com.dependencyintelligence.repository.application.port.out.LocalRepositorySourceStore;
import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.domain.Repository;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.shared.domain.OrganizationId;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

public final class ImportLocalFolderUseCase implements ImportLocalFolder {
    private final OrganizationLookup organizations;
    private final RepositoryStore repositories;
    private final LocalRepositorySourceStore sources;
    private final Clock clock;

    public ImportLocalFolderUseCase(OrganizationLookup organizations, RepositoryStore repositories,
                                   LocalRepositorySourceStore sources, Clock clock) {
        this.organizations = organizations; this.repositories = repositories; this.sources = sources; this.clock = clock;
    }

    @Override public LocalFolderImportResult execute(LocalFolderImportCommand command) {
        var organizationId = new OrganizationId(command.organizationId());
        if (!organizations.exists(organizationId)) throw new ResourceNotFoundException("Organization");
        String name = validateName(command.name());
        if (command.files().isEmpty() || command.files().size() > MAX_FILES) throw new IllegalArgumentException("Selecciona entre 1 y 50.000 archivos.");
        long total = 0;
        var paths = new HashSet<String>();
        var normalizedFiles = new java.util.ArrayList<LocalFolderImportCommand.ProjectFile>(command.files().size());
        String selectedRoot = null;
        for (var file : command.files()) {
            String path = validatePath(file.relativePath());
            String root = file.relativePath().substring(0, file.relativePath().indexOf('/'));
            if (selectedRoot == null) selectedRoot = root;
            else if (!selectedRoot.equals(root)) throw new IllegalArgumentException("Selecciona una sola carpeta de proyecto.");
            if (!paths.add(path.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("La carpeta contiene rutas duplicadas.");
            if (file.size() < 0 || file.size() > MAX_LOCAL_CONTENT_BYTES - total)
                throw new IllegalArgumentException("La carpeta supera el límite de 300 MiB.");
            total += file.size();
            normalizedFiles.add(new LocalFolderImportCommand.ProjectFile(path, file.size(), file.content()));
        }

        UUID id = UUID.randomUUID();
        var repository = new Repository(new RepositoryId(id), organizationId, "local", id.toString(), name,
            "local", clock.instant().truncatedTo(ChronoUnit.MICROS));
        repositories.add(repository);
        var stored = sources.store(command.organizationId(), id, command.userId(), normalizedFiles);
        return new LocalFolderImportResult(id, name, stored.fileCount(), stored.sizeBytes());
    }

    private static String validateName(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 200 || value.contains("/") || value.contains("\\"))
            throw new IllegalArgumentException("El nombre de la carpeta debe tener entre 1 y 200 caracteres.");
        return value.strip();
    }

    private static String validatePath(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_PATH_LENGTH || value.startsWith("/") || value.contains("\\") || value.contains(":") || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("La carpeta contiene una ruta de archivo no válida.");
        String[] parts = value.split("/", -1);
        if (parts.length < 2 || java.util.Arrays.stream(parts).anyMatch(part -> part.isBlank() || part.equals(".") || part.equals("..")))
            throw new IllegalArgumentException("La carpeta contiene una ruta de archivo no válida.");
        for (int i = 0; i < parts.length - 1; i++) {
            if (java.util.Set.of(".git", "node_modules", "target", "dist", "build", ".angular").contains(parts[i].toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Quita .git, node_modules y carpetas de compilación antes de importar el proyecto.");
        }
        String filename = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        if (isSensitive(filename))
            throw new IllegalArgumentException("La carpeta contiene un archivo de credenciales. Quítalo antes de importar.");
        return String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length));
    }

    private static boolean isSensitive(String filename) {
        if (java.util.Set.of(".env.example", ".env.sample", ".env.template").contains(filename)) return false;
        return filename.equals(".env") || filename.startsWith(".env.")
            || java.util.Set.of(".npmrc", ".pypirc", "id_rsa", "id_ed25519", "credentials.json").contains(filename)
            || filename.endsWith(".pem") || filename.endsWith(".key")
            || filename.endsWith(".p12") || filename.endsWith(".pfx");
    }
}
