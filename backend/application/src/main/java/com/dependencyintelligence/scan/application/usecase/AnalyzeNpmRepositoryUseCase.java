package com.dependencyintelligence.scan.application.usecase;

import com.dependencyintelligence.scan.application.dto.MavenInventory;
import com.dependencyintelligence.scan.application.port.in.AnalyzeNpmRepository;
import com.dependencyintelligence.scan.application.port.out.MavenAnalysisStore;
import com.dependencyintelligence.scan.application.port.out.NpmLockfileReader;
import com.dependencyintelligence.scan.application.port.out.RepositoryAnalysisSource;
import java.io.IOException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

public final class AnalyzeNpmRepositoryUseCase implements AnalyzeNpmRepository {
    private final RepositoryAnalysisSource sources;
    private final NpmLockfileReader reader;
    private final MavenAnalysisStore store;
    private final Clock clock;

    public AnalyzeNpmRepositoryUseCase(RepositoryAnalysisSource sources, NpmLockfileReader reader,
                                       MavenAnalysisStore store, Clock clock) {
        this.sources = sources; this.reader = reader; this.store = store; this.clock = clock;
    }

    @Override public MavenInventory run(UUID organizationId, UUID repositoryId) {
        try (var source = sources.open(organizationId, repositoryId)) {
            var analysis = reader.read(source.content());
            if (analysis.manifestCount() == 0) throw new IllegalArgumentException("No se encontró package-lock.json compatible.");
            return store.save("cdxgen-npm", UUID.randomUUID(), organizationId, repositoryId,
                source.provider(), source.sha256(), source.commitSha(), clock.instant().truncatedTo(ChronoUnit.MICROS), analysis);
        } catch (IOException failure) {
            throw new IllegalStateException("No se pudo cerrar el archivo de código fuente.", failure);
        }
    }

    @Override public Optional<MavenInventory> latest(UUID organizationId, UUID repositoryId) {
        return store.latest("cdxgen-npm", organizationId, repositoryId);
    }
}
