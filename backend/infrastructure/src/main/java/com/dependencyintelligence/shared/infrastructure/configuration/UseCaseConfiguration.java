package com.dependencyintelligence.shared.infrastructure.configuration;

import com.dependencyintelligence.organization.application.port.out.OrganizationLookup;
import com.dependencyintelligence.repository.application.port.in.FindRepository;
import com.dependencyintelligence.repository.application.port.in.RegisterRepository;
import com.dependencyintelligence.repository.application.port.in.ImportLocalFolder;
import com.dependencyintelligence.repository.application.port.in.ListLocalRepositories;
import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.application.port.out.LocalRepositorySourceStore;
import com.dependencyintelligence.repository.application.usecase.FindRepositoryUseCase;
import com.dependencyintelligence.repository.application.usecase.ImportLocalFolderUseCase;
import com.dependencyintelligence.repository.application.usecase.ListLocalRepositoriesUseCase;
import com.dependencyintelligence.repository.application.usecase.RegisterRepositoryUseCase;
import com.dependencyintelligence.scan.application.port.in.RequestScan;
import com.dependencyintelligence.scan.application.port.out.ScanStore;
import com.dependencyintelligence.scan.application.usecase.RequestScanUseCase;
import com.dependencyintelligence.scan.application.port.in.AnalyzeMavenRepository;
import com.dependencyintelligence.scan.application.port.out.RepositoryAnalysisSource;
import com.dependencyintelligence.scan.application.port.out.MavenManifestReader;
import com.dependencyintelligence.scan.application.port.out.MavenAnalysisStore;
import com.dependencyintelligence.scan.application.usecase.AnalyzeMavenRepositoryUseCase;
import com.dependencyintelligence.scan.application.port.in.AnalyzeNpmRepository;
import com.dependencyintelligence.scan.application.port.out.NpmLockfileReader;
import com.dependencyintelligence.scan.application.usecase.AnalyzeNpmRepositoryUseCase;
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

    @Bean ImportLocalFolder importLocalFolder(OrganizationLookup organizations, RepositoryStore repositories,
                                               LocalRepositorySourceStore sources, Clock clock, TenantTransactions transactions) {
        var useCase = new ImportLocalFolderUseCase(organizations, repositories, sources, clock);
        return command -> transactions.execute(command.organizationId(), () -> useCase.execute(command));
    }

    @Bean ListLocalRepositories listLocalRepositories(OrganizationLookup organizations, LocalRepositorySourceStore sources,
                                                      TenantTransactions transactions) {
        var useCase = new ListLocalRepositoriesUseCase(organizations, sources);
        return organization -> transactions.execute(organization, () -> useCase.execute(organization));
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

    @Bean AnalyzeMavenRepository analyzeMavenRepository(RepositoryAnalysisSource source, MavenManifestReader reader,
            MavenAnalysisStore store, Clock clock) {
        return new AnalyzeMavenRepositoryUseCase(source, reader, store, clock);
    }
    @Bean AnalyzeNpmRepository analyzeNpmRepository(RepositoryAnalysisSource source,NpmLockfileReader reader,MavenAnalysisStore store,Clock clock){return new AnalyzeNpmRepositoryUseCase(source,reader,store,clock);}
}
