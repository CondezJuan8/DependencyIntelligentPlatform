package com.dependencyintelligence.scan.application.usecase;

import com.dependencyintelligence.scan.application.dto.MavenInventory;
import com.dependencyintelligence.scan.application.port.out.MavenAnalysisStore;
import com.dependencyintelligence.scan.application.port.out.RepositoryAnalysisSource;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

public final class AnalyzeRepositoryUseCase {
    private final RepositoryAnalysisSource sources;
    private final AnalyzeMavenRepositoryUseCase maven;
    private final AnalyzeNpmRepositoryUseCase npm;
    private final MavenAnalysisStore scans;
    private final Clock clock;

    public AnalyzeRepositoryUseCase(RepositoryAnalysisSource sources, AnalyzeMavenRepositoryUseCase maven,
                                    AnalyzeNpmRepositoryUseCase npm, MavenAnalysisStore scans, Clock clock) {
        this.sources = sources;
        this.maven = maven;
        this.npm = npm;
        this.scans = scans;
        this.clock = clock;
    }

    public List<Result> run(UUID organizationId, UUID repositoryId) {
        try (var source = sources.open(organizationId, repositoryId)) {
            return List.of(
                run("MAVEN", "cdxgen-maven", organizationId, repositoryId, source,
                    () -> maven.analyze(organizationId, repositoryId, source, true)),
                run("NPM", "cdxgen-npm", organizationId, repositoryId, source,
                    () -> npm.analyze(organizationId, repositoryId, source, true)));
        } catch (IOException failure) {
            throw new ManifestReadException("No se pudo cerrar el archivo de código fuente.", failure);
        }
    }

    private Result run(String ecosystem, String scanner, UUID organizationId, UUID repositoryId,
            RepositoryAnalysisSource.Archive source, Supplier<Optional<MavenInventory>> analyze) {
        try {
            return analyze.get().map(scan -> new Result(ecosystem,
                    scan.manifestCount() == 0 ? Status.NO_MANIFEST : Status.COMPLETED, scan, scan.coverageMessage()))
                .orElseGet(() -> new Result(ecosystem, Status.NO_MANIFEST, null, null));
        } catch (ManifestReadException failure) {
            scans.saveFailure(scanner, organizationId, repositoryId, source.provider(), source.sha256(),
                source.commitSha(), clock.instant().truncatedTo(ChronoUnit.MICROS), failure.getMessage());
            return new Result(ecosystem, Status.FAILED, null, failure.getMessage());
        }
    }

    public enum Status { COMPLETED, NO_MANIFEST, FAILED }
    public record Result(String ecosystem, Status status, MavenInventory scan, String message) {}
}
