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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
                               Clock clock, PlatformTransactionManager manager) {
        var useCase = new ImportSbomUseCase(repositories, parser, imports, clock);
        var transaction = new TransactionTemplate(manager);
        return command -> transaction.execute(status -> useCase.execute(command));
    }

    @Bean RegisterRepository registerRepository(OrganizationLookup organizations, RepositoryStore repositories,
                                                Clock clock, PlatformTransactionManager manager) {
        var useCase = new RegisterRepositoryUseCase(organizations, repositories, clock);
        var transaction = new TransactionTemplate(manager);
        return command -> transaction.execute(status -> useCase.execute(command));
    }

    @Bean FindRepository findRepository(RepositoryStore repositories) {
        return new FindRepositoryUseCase(repositories);
    }

    @Bean RequestScan requestScan(RepositoryStore repositories, ScanStore scans,
                                 Clock clock, PlatformTransactionManager manager) {
        var useCase = new RequestScanUseCase(repositories, scans, clock);
        var transaction = new TransactionTemplate(manager);
        return command -> transaction.execute(status -> useCase.execute(command));
    }
}
