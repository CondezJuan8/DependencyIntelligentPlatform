package com.dependencyintelligence.shared.infrastructure.configuration;

import com.dependencyintelligence.organization.application.port.out.OrganizationLookup;
import com.dependencyintelligence.repository.application.port.in.FindRepository;
import com.dependencyintelligence.repository.application.port.in.RegisterRepository;
import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.application.usecase.FindRepositoryUseCase;
import com.dependencyintelligence.repository.application.usecase.RegisterRepositoryUseCase;
import com.dependencyintelligence.scan.application.port.in.RequestScan;
import com.dependencyintelligence.scan.application.port.out.ScanStore;
import com.dependencyintelligence.scan.application.usecase.RequestScanUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import java.time.Clock;
import com.dependencyintelligence.sbom.application.port.in.ImportSbom;
import com.dependencyintelligence.sbom.application.port.out.SbomParser;
import com.dependencyintelligence.sbom.application.port.out.SbomImportStore;
import com.dependencyintelligence.sbom.application.usecase.ImportSbomUseCase;

/** Composition root owns framework wiring and use case transaction boundaries. */
@Configuration
public class UseCaseConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean ImportSbom importSbom(RepositoryStore repositories, SbomParser parser, SbomImportStore imports,
                               Clock clock, TenantTransactions transactions) {
        var useCase = new ImportSbomUseCase(repositories, parser, imports, clock);
        return command -> transactions.execute(command.organizationId(), () -> useCase.execute(command));
    }

    @Bean RegisterRepository registerRepository(OrganizationLookup organizations, RepositoryStore repositories,
                                                Clock clock, TenantTransactions transactions) {
        var useCase = new RegisterRepositoryUseCase(organizations, repositories, clock);
        return command -> transactions.execute(command.organizationId(), () -> useCase.execute(command));
    }

    @Bean FindRepository findRepository(RepositoryStore repositories, TenantTransactions transactions) {
        var useCase = new FindRepositoryUseCase(repositories);
        return (organizationId, repositoryId) -> transactions.execute(organizationId, () -> useCase.execute(organizationId, repositoryId));
    }

    @Bean RequestScan requestScan(RepositoryStore repositories, ScanStore scans,
                                 Clock clock, TenantTransactions transactions) {
        var useCase = new RequestScanUseCase(repositories, scans, clock);
        return command -> transactions.execute(command.organizationId(), () -> useCase.execute(command));
    }
}
