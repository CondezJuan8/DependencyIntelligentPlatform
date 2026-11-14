package com.dependencyintelligence.scan.application.usecase;

import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.domain.RepositoryId;
import com.dependencyintelligence.scan.application.dto.RequestScanCommand;
import com.dependencyintelligence.scan.application.dto.ScanResult;
import com.dependencyintelligence.scan.application.port.in.RequestScan;
import com.dependencyintelligence.scan.application.port.out.ScanStore;
import com.dependencyintelligence.scan.domain.Scan;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.shared.domain.OrganizationId;
import java.time.Clock;
import java.util.UUID;

public final class RequestScanUseCase implements RequestScan {
    private final RepositoryStore repositories;
    private final ScanStore scans;
    private final Clock clock;

    public RequestScanUseCase(RepositoryStore repositories, ScanStore scans, Clock clock) {
        this.repositories = repositories;
        this.scans = scans;
        this.clock = clock;
    }

    @Override
    public ScanResult execute(RequestScanCommand command) {
        var organizationId = new OrganizationId(command.organizationId());
        var repositoryId = new RepositoryId(command.repositoryId());
        repositories.find(organizationId, repositoryId).orElseThrow(() -> new ResourceNotFoundException("Repository"));
        return ScanResult.from(scans.add(Scan.request(UUID.randomUUID(), organizationId, repositoryId,
            command.gitReference(), clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS))));
    }
}
