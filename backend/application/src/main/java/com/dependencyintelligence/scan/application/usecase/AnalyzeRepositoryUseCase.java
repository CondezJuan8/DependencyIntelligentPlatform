package com.dependencyintelligence.scan.application.usecase;

import com.dependencyintelligence.scan.application.dto.MavenInventory;
import com.dependencyintelligence.scan.application.port.out.MavenAnalysisStore;
import com.dependencyintelligence.scan.application.port.out.RepositoryAnalysisSource;
import java.io.IOException;
import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
        return run(organizationId, repositoryId, null, ignored -> { }, ignored -> { }, () -> false);
    }

    public List<Result> run(UUID organizationId, UUID repositoryId, UUID jobId,
            Consumer<RepositoryAnalysisSource.Archive> sourceReady, Consumer<String> stageCompleted,
            BooleanSupplier cancelled) {
        try (var source = sources.open(organizationId, repositoryId)) {
            sourceReady.accept(source);
            ensureNotCancelled(cancelled);
            Result mavenResult = run("MAVEN", "cdxgen-maven", organizationId, repositoryId, source, jobId,
                () -> maven.analyze(organizationId, repositoryId, source, true, jobId), cancelled);
            stageCompleted.accept("MAVEN_FINISHED");
            ensureNotCancelled(cancelled);
            Result npmResult = run("NPM", "cdxgen-npm", organizationId, repositoryId, source, jobId,
                () -> npm.analyze(organizationId, repositoryId, source, true, jobId), cancelled);
            stageCompleted.accept("NPM_FINISHED");
            return List.of(mavenResult, npmResult);
        } catch (IOException failure) {
            throw new ManifestReadException("No se pudo cerrar el archivo de código fuente.", failure);
        }
    }

    private Result run(String ecosystem, String scanner, UUID organizationId, UUID repositoryId,
            RepositoryAnalysisSource.Archive source, UUID jobId, Supplier<Optional<MavenInventory>> analyze,
            BooleanSupplier cancelled) {
        ensureNotCancelled(cancelled);
        if (jobId != null) {
            Optional<MavenInventory> previous = scans.findForJob(jobId, scanner, organizationId, repositoryId);
            if (previous.isPresent()) {
                MavenInventory saved = previous.get();
                return new Result(ecosystem, saved.manifestCount() == 0 ? Status.NO_MANIFEST : Status.COMPLETED,
                    saved, saved.coverageMessage());
            }
        }
        try {
            return analyze.get().map(scan -> new Result(ecosystem,
                    scan.manifestCount() == 0 ? Status.NO_MANIFEST : Status.COMPLETED, scan, scan.coverageMessage()))
                .orElseGet(() -> new Result(ecosystem, Status.NO_MANIFEST, null, null));
        } catch (ManifestReadException failure) {
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            if (jobId == null) {
                scans.saveFailure(scanner, organizationId, repositoryId, source.provider(), source.sha256(),
                    source.commitSha(), now, failure.getMessage());
            } else {
                scans.saveFailureForJob(jobId, scanner, organizationId, repositoryId, source.provider(),
                    source.sha256(), source.commitSha(), now, failure.getMessage());
            }
            return new Result(ecosystem, Status.FAILED, null, failure.getMessage());
        }
    }

    private static void ensureNotCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) throw new CancellationException("El análisis fue cancelado.");
    }

    public enum Status { COMPLETED, NO_MANIFEST, FAILED }
    public record Result(String ecosystem, Status status, MavenInventory scan, String message) { }
}
