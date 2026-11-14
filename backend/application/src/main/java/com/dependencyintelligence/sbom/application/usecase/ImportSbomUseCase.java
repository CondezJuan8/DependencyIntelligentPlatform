package com.dependencyintelligence.sbom.application.usecase;

import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.sbom.application.dto.ImportSbomCommand;
import com.dependencyintelligence.sbom.application.dto.ImportSbomResult;
import com.dependencyintelligence.sbom.application.port.in.ImportSbom;
import com.dependencyintelligence.sbom.application.port.out.SbomImportStore;
import com.dependencyintelligence.sbom.application.port.out.SbomParser;
import com.dependencyintelligence.scan.domain.Scan;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.shared.domain.OrganizationId;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

public final class ImportSbomUseCase implements ImportSbom {
    private final RepositoryStore repositories;
    private final SbomParser parser;
    private final SbomImportStore imports;
    private final Clock clock;

    public ImportSbomUseCase(RepositoryStore repositories, SbomParser parser, SbomImportStore imports, Clock clock) {
        this.repositories = repositories;
        this.parser = parser;
        this.imports = imports;
        this.clock = clock;
    }

    @Override
    public ImportSbomResult execute(ImportSbomCommand command) {
        var organizationId = new OrganizationId(command.organizationId());
        var repositoryId = new RepositoryId(command.repositoryId());
        repositories.find(organizationId, repositoryId).orElseThrow(() -> new ResourceNotFoundException("Repository"));
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required", exception); }
        // The caller owns the stream. Parse and validate everything before the first write.
        var sbom = parser.parse(new DigestInputStream(command.content(), digest));
        var sha256 = HexFormat.of().formatHex(digest.digest());
        var snapshot = Scan.imported(UUID.randomUUID(), organizationId, repositoryId,
            clock.instant().truncatedTo(ChronoUnit.MICROS));
        imports.save(snapshot, sbom, sha256);
        return new ImportSbomResult(snapshot.id(), command.organizationId(), command.repositoryId(),
            sbom.components().size(), snapshot.finishedAt(), sha256);
    }
}
