package com.dependencyintelligence.shared.infrastructure.configuration;

import com.dependencyintelligence.identity.application.port.in.FindAccountWorkspace;
import com.dependencyintelligence.inventory.application.port.in.FilterInventory;
import com.dependencyintelligence.inventory.application.port.out.InventoryCatalogReader;
import com.dependencyintelligence.inventory.application.usecase.FilterInventoryUseCase;

import com.dependencyintelligence.organization.application.port.out.OrganizationLookup;
import com.dependencyintelligence.repository.application.port.in.FindRepository;
import com.dependencyintelligence.repository.application.port.in.RegisterRepository;
import com.dependencyintelligence.repository.application.port.in.ImportLocalFolder;
import com.dependencyintelligence.repository.application.port.in.ListLocalRepositories;
import com.dependencyintelligence.repository.application.port.in.DeleteRepository;
import com.dependencyintelligence.repository.application.port.in.FindRepositoryVulnerabilityEvidence;
import com.dependencyintelligence.repository.application.port.out.RepositoryCodeArchive;
import com.dependencyintelligence.repository.application.port.out.RepositoryStore;
import com.dependencyintelligence.repository.application.port.out.LocalRepositorySourceStore;
import com.dependencyintelligence.repository.application.usecase.FindRepositoryUseCase;
import com.dependencyintelligence.repository.application.usecase.ImportLocalFolderUseCase;
import com.dependencyintelligence.repository.application.usecase.ListLocalRepositoriesUseCase;
import com.dependencyintelligence.repository.application.usecase.DeleteRepositoryUseCase;
import com.dependencyintelligence.repository.application.usecase.FindRepositoryVulnerabilityEvidenceUseCase;
import com.dependencyintelligence.repository.application.usecase.RegisterRepositoryUseCase;
import com.dependencyintelligence.scan.application.port.in.RequestScan;
import com.dependencyintelligence.scan.application.port.in.FindScanHistory;
import com.dependencyintelligence.scan.application.port.out.ScanHistoryReader;
import com.dependencyintelligence.scan.application.port.out.ScanStore;
import com.dependencyintelligence.scan.application.usecase.RequestScanUseCase;
import com.dependencyintelligence.scan.application.usecase.FindScanHistoryUseCase;
import com.dependencyintelligence.scan.application.port.in.AnalyzeMavenRepository;
import com.dependencyintelligence.scan.application.port.out.RepositoryAnalysisSource;
import com.dependencyintelligence.scan.application.port.out.MavenManifestReader;
import com.dependencyintelligence.scan.application.port.out.MavenAnalysisStore;
import com.dependencyintelligence.scan.application.usecase.AnalyzeRepositoryUseCase;
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
import com.dependencyintelligence.document.application.port.in.ManageDocuments;
import com.dependencyintelligence.document.application.port.out.DocumentLibrary;
import com.dependencyintelligence.document.application.usecase.ManageDocumentsUseCase;
import com.dependencyintelligence.assistant.application.port.in.AskAssistant;
import com.dependencyintelligence.assistant.application.port.in.IndexDocuments;
import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.AnswerGeneration;
import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.ConversationStore;
import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.Embeddings;
import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.EvidenceSearch;
import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.IndexableChunks;
import com.dependencyintelligence.assistant.application.usecase.AskAssistantUseCase;
import com.dependencyintelligence.assistant.application.usecase.IndexDocumentsUseCase;

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

    @Bean DeleteRepository deleteRepository(RepositoryStore repositories, LocalRepositorySourceStore sources, TenantTransactions transactions) {
        var useCase = new DeleteRepositoryUseCase(repositories, sources);
        return (organizationId, repositoryId) -> transactions.execute(organizationId, () -> { useCase.execute(organizationId, repositoryId); return null; });
    }

    @Bean FindRepositoryVulnerabilityEvidence findRepositoryVulnerabilityEvidence(RepositoryCodeArchive archives) {
        return new FindRepositoryVulnerabilityEvidenceUseCase(archives);
    }

    @Bean RequestScan requestScan(RepositoryStore repositories, ScanStore scans,
                                 Clock clock, TenantTransactions transactions) {
        var useCase = new RequestScanUseCase(repositories, scans, clock);
        return command -> transactions.execute(command.organizationId(), () -> useCase.execute(command));
    }

    @Bean FindScanHistory findScanHistory(ScanHistoryReader history) {
        return new FindScanHistoryUseCase(history);
    }

    @Bean AnalyzeMavenRepositoryUseCase analyzeMavenRepository(RepositoryAnalysisSource source, MavenManifestReader reader,
            MavenAnalysisStore store, Clock clock) {
        return new AnalyzeMavenRepositoryUseCase(source, reader, store, clock);
    }
    @Bean AnalyzeNpmRepositoryUseCase analyzeNpmRepository(RepositoryAnalysisSource source, NpmLockfileReader reader,
            MavenAnalysisStore store, Clock clock) {
        return new AnalyzeNpmRepositoryUseCase(source, reader, store, clock);
    }

    @Bean AnalyzeRepositoryUseCase analyzeRepository(RepositoryAnalysisSource source, AnalyzeMavenRepositoryUseCase maven,
            AnalyzeNpmRepositoryUseCase npm, MavenAnalysisStore scans, Clock clock) {
        return new AnalyzeRepositoryUseCase(source, maven, npm, scans, clock);
    }

    @Bean FilterInventory filterInventory(FindAccountWorkspace workspace, InventoryCatalogReader reader) {
        return new FilterInventoryUseCase(workspace, reader);
    }

    @Bean ManageDocuments manageDocuments(DocumentLibrary documents) { return new ManageDocumentsUseCase(documents); }
    @Bean AskAssistant askAssistant(Embeddings embeddings, EvidenceSearch search, AnswerGeneration answers, ConversationStore conversations) {
        return new AskAssistantUseCase(embeddings, search, answers, conversations);
    }
    @Bean IndexDocuments indexDocuments(Embeddings embeddings, IndexableChunks chunks) { return new IndexDocumentsUseCase(embeddings, chunks); }

}
