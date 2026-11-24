package com.dependencyintelligence.scan.application.usecase;

import com.dependencyintelligence.scan.application.dto.MavenInventory;
import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.port.in.AnalyzeMavenRepository;
import com.dependencyintelligence.scan.application.port.out.MavenAnalysisStore;
import com.dependencyintelligence.scan.application.port.out.MavenManifestReader;
import com.dependencyintelligence.scan.application.port.out.RepositoryAnalysisSource;
import java.io.IOException;
import java.time.Instant;
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
            return analyze(organizationId, repositoryId, source, false)
                .orElseThrow(() -> new IllegalArgumentException("Este repositorio no contiene un proyecto Maven compatible."));
        } catch (IOException failure) {
            throw new IllegalStateException("No se pudo cerrar el archivo de código fuente.", failure);
        }
    }

    Optional<MavenInventory> analyze(UUID organizationId, UUID repositoryId, RepositoryAnalysisSource.Archive source,
                                     boolean recordMissingManifest) {
        return analyze(organizationId, repositoryId, source, recordMissingManifest, null);
    }

    Optional<MavenInventory> analyze(UUID organizationId, UUID repositoryId, RepositoryAnalysisSource.Archive source,
                                     boolean recordMissingManifest, UUID jobId) {
        ManifestAnalysis analysis;
        try (var input = source.content()) {
            analysis = manifests.read(input);
        } catch (IOException failure) {
            throw new ManifestReadException("No se pudo leer la fuente del repositorio.", failure);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new ManifestReadException(failure.getMessage(), failure);
        }
        if (analysis.manifestCount() == 0 && !recordMissingManifest) return Optional.empty();
        UUID scanId = UUID.randomUUID();
        Instant analyzedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        MavenInventory saved = jobId == null
            ? store.save("cdxgen-maven", scanId, organizationId, repositoryId, source.provider(), source.sha256(),
                source.commitSha(), analyzedAt, analysis)
            : store.saveForJob(jobId, "cdxgen-maven", scanId, organizationId, repositoryId, source.provider(),
                source.sha256(), source.commitSha(), analyzedAt, analysis);
        return Optional.of(saved);
    }

    @Override public Optional<MavenInventory> latest(UUID organizationId, UUID repositoryId) {
        return store.latest("cdxgen-maven", organizationId, repositoryId);
    }

    @Override public Optional<MavenInventory> find(UUID organizationId, UUID repositoryId, UUID scanId) {
        return store.findById("cdxgen-maven", organizationId, repositoryId, scanId);
    }
}
