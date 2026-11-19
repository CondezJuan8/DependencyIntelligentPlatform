package com.dependencyintelligence.scan.application.usecase;

import com.dependencyintelligence.scan.application.dto.MavenInventory;
import com.dependencyintelligence.scan.application.port.in.AnalyzeMavenRepository;
import com.dependencyintelligence.scan.application.port.out.MavenAnalysisStore;
import com.dependencyintelligence.scan.application.port.out.MavenManifestReader;
import com.dependencyintelligence.scan.application.port.out.RepositoryAnalysisSource;
import java.io.IOException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

public final class AnalyzeMavenRepositoryUseCase implements AnalyzeMavenRepository {
    private final RepositoryAnalysisSource sources;
    private final MavenManifestReader manifests;
    private final MavenAnalysisStore store;
    private final Clock clock;

    public AnalyzeMavenRepositoryUseCase(RepositoryAnalysisSource sources, MavenManifestReader manifests,
                                         MavenAnalysisStore store, Clock clock) {
        this.sources = sources; this.manifests = manifests; this.store = store; this.clock = clock;
    }

    @Override public MavenInventory run(UUID organizationId, UUID repositoryId) {
        try (var source = sources.open(organizationId, repositoryId)) {
            var analysis = manifests.read(source.content());
            if (analysis.manifestCount() == 0)
                throw new IllegalArgumentException("Este repositorio no contiene pom.xml. El análisis npm llegará después.");
            return store.save(UUID.randomUUID(), organizationId, repositoryId, source.provider(),
                source.sha256(), source.commitSha(), clock.instant().truncatedTo(ChronoUnit.MICROS), analysis);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo cerrar el archivo de código fuente.", exception);
        }
    }

    @Override public Optional<MavenInventory> latest(UUID organizationId, UUID repositoryId) {
        return store.latest(organizationId, repositoryId);
    }
}
