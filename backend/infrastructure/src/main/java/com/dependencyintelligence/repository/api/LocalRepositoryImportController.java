package com.dependencyintelligence.repository.api;

import com.dependencyintelligence.identity.infrastructure.security.AuthorizedRepositoryActions;
import com.dependencyintelligence.repository.application.dto.LocalFolderImportCommand;
import com.dependencyintelligence.repository.application.dto.LocalFolderImportResult;
import com.dependencyintelligence.repository.application.port.in.ImportLocalFolder;
import com.dependencyintelligence.repository.application.port.in.ListLocalRepositories;
import com.dependencyintelligence.repository.application.dto.LocalRepositorySummary;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories")
public class LocalRepositoryImportController {
    private final ImportLocalFolder importer;
    private final ListLocalRepositories localRepositories;
    private final AuthorizedRepositoryActions authorization;

    public LocalRepositoryImportController(ImportLocalFolder importer, ListLocalRepositories localRepositories,
                                           AuthorizedRepositoryActions authorization) {
        this.importer = importer; this.localRepositories = localRepositories; this.authorization = authorization;
    }

    @org.springframework.web.bind.annotation.GetMapping("/local-folders")
    public List<LocalRepositorySummary> list(@org.springframework.web.bind.annotation.PathVariable UUID organizationId, Principal principal) {
        return authorization.execute(principal, organizationId, null, "repository.manage", () -> localRepositories.execute(organizationId));
    }

    @PostMapping(path = "/local-folder", consumes = "multipart/form-data")
    public LocalFolderImportResult upload(@org.springframework.web.bind.annotation.PathVariable UUID organizationId,
            Principal principal, @RequestParam("name") String name, @RequestParam("paths") List<String> paths,
            @RequestParam("files") List<MultipartFile> files) {
        if (files.isEmpty() || files.size() != paths.size() || files.size() > 2_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una carpeta con hasta 2.000 archivos.");
        var projectFiles = new ArrayList<LocalFolderImportCommand.ProjectFile>(files.size());
        for (int index = 0; index < files.size(); index++) {
            var file = files.get(index);
            projectFiles.add(new LocalFolderImportCommand.ProjectFile(paths.get(index), file.getSize(), file::getInputStream));
        }
        var command = new LocalFolderImportCommand(organizationId, UUID.fromString(principal.getName()), name, projectFiles);
        try {
            return authorization.execute(principal, organizationId, null, "repository.manage", () -> importer.execute(command));
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        }
    }
}
