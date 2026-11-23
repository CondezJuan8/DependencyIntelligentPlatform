package com.dependencyintelligence.scan.application.usecase;

import com.dependencyintelligence.scan.application.dto.MavenInventory;
import com.dependencyintelligence.scan.application.port.out.RepositoryAnalysisSource;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class AnalyzeRepositoryUseCase {
    private final RepositoryAnalysisSource sources;
    private final AnalyzeMavenRepositoryUseCase maven;
    private final AnalyzeNpmRepositoryUseCase npm;

    public AnalyzeRepositoryUseCase(RepositoryAnalysisSource sources, AnalyzeMavenRepositoryUseCase maven,
                                    AnalyzeNpmRepositoryUseCase npm) {
        this.sources = sources;
        this.maven = maven;
        this.npm = npm;
    }

    public List<Result> run(UUID organizationId, UUID repositoryId) {
        try (var source = sources.open(organizationId, repositoryId)) {
            return List.of(
                run("MAVEN", () -> maven.analyze(organizationId, repositoryId, source, true)),
                run("NPM", () -> npm.analyze(organizationId, repositoryId, source, true)));
        } catch (IOException failure) {
            throw new ManifestReadException("No se pudo cerrar el archivo de código fuente.", failure);
        }
    }

    private static Result run(String ecosystem, Supplier<Optional<MavenInventory>> analyze) {
        try {
            return analyze.get().map(scan -> new Result(ecosystem,
                    scan.manifestCount() == 0 ? Status.NO_MANIFEST : Status.COMPLETED, scan, scan.coverageMessage()))
                .orElseGet(() -> new Result(ecosystem, Status.NO_MANIFEST, null, null));
        } catch (ManifestReadException failure) {
            return new Result(ecosystem, Status.FAILED, null, failure.getMessage());
        }
    }

    public enum Status { COMPLETED, NO_MANIFEST, FAILED }
    public record Result(String ecosystem, Status status, MavenInventory scan, String message) {}
}
