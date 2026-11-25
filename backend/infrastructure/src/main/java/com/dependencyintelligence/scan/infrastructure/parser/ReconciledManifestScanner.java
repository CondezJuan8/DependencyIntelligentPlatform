package com.dependencyintelligence.scan.infrastructure.parser;

import static com.dependencyintelligence.repository.application.RepositorySourceLimits.MAX_REPLAY_ZIP_BYTES;

import com.dependencyintelligence.component.domain.PackageCoordinates;
import com.dependencyintelligence.scan.application.dto.ManifestAnalysis;
import com.dependencyintelligence.scan.application.dto.ManifestDependency;
import com.dependencyintelligence.scan.application.port.out.CycloneDxGenerator;
import com.dependencyintelligence.scan.application.port.out.CycloneDxGenerator.Ecosystem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

final class ReconciledManifestScanner {
    private final CycloneDxGenerator generator;
    private final CycloneDxManifestMapper mapper;

    ReconciledManifestScanner(CycloneDxGenerator generator, CycloneDxManifestMapper mapper) {
        this.generator = generator;
        this.mapper = mapper;
    }

    ManifestAnalysis read(InputStream archive, Ecosystem ecosystem, Function<InputStream, ManifestAnalysis> readManifests) {
        Path replay = null;
        try {
            replay = Files.createTempFile("dependency-intelligence-scan-", ".zip");
            copyBounded(archive, replay);

            ManifestAnalysis local = null;
            RuntimeException localFailure = null;
            try (var input = Files.newInputStream(replay)) {
                local = readManifests.apply(input);
            } catch (IllegalArgumentException failure) {
                localFailure = failure;
            }
            if (local != null && local.manifestCount() == 0) return local;

            ManifestAnalysis sbom;
            try (var input = Files.newInputStream(replay)) {
                sbom = mapper.map(generator.generate(input, ecosystem), ecosystem.name());
            } catch (IllegalArgumentException | IllegalStateException failure) {
                if (local == null || (local.dependencies().isEmpty() && local.unresolvedCount() > 0)) {
                    if (localFailure != null) failure.addSuppressed(localFailure);
                    throw failure;
                }
                return partial(local, "cdxgen no está disponible para esta fuente. " + recoveryMessage(ecosystem, local));
            }

            if (local == null) {
                if (sbom.dependencies().isEmpty()) throw localFailure;
                return partial(sbom, "No se pudo contrastar CycloneDX con los manifiestos: " + localFailure.getMessage());
            }
            if (sbom.dependencies().isEmpty()) {
                if (ecosystem == Ecosystem.NPM && local.dependencies().isEmpty() && local.unresolvedCount() == 0) return local;
                String detail = sbom.coverageMessage() == null
                    ? "cdxgen no devolvió componentes utilizables."
                    : sbom.coverageMessage();
                return partial(local, detail + " " + recoveryMessage(ecosystem, local));
            }
            return reconcile(local, sbom, ecosystem);
        } catch (IOException failure) {
            throw new IllegalArgumentException("No se pudo preparar la fuente para el análisis.", failure);
        } finally {
            if (replay != null) try { Files.deleteIfExists(replay); } catch (IOException ignored) { }
        }
    }

    private static String recoveryMessage(Ecosystem ecosystem, ManifestAnalysis local) {
        if (ecosystem == Ecosystem.NPM)
            return "El inventario se recuperó directamente de package-lock.json.";
        return local.dependencies().isEmpty() && local.unresolvedCount() > 0
            ? "Los POM contienen referencias sin versión exacta verificable."
            : "Se recuperaron dependencias directas desde los POM. Las transitivas de Maven requieren evidencia adicional.";
    }

    private static ManifestAnalysis partial(ManifestAnalysis analysis, String message) {
        return new ManifestAnalysis(analysis.dependencies(), analysis.manifestCount(),
            analysis.unresolvedCount(), "PARTIAL", message);
    }

    private static ManifestAnalysis reconcile(ManifestAnalysis local, ManifestAnalysis sbom, Ecosystem ecosystem) {
        // Keep lockfile paths and scopes for npm. Maven CycloneDX paths identify SBOM components independently of POM paths.
        ManifestAnalysis primary = ecosystem == Ecosystem.NPM ? local : sbom;
        ManifestAnalysis secondary = ecosystem == Ecosystem.NPM ? sbom : local;
        var dependencies = new ArrayList<>(primary.dependencies());
        var byPackage = new HashMap<Identity, List<ManifestDependency>>();
        for (var dependency : primary.dependencies())
            byPackage.computeIfAbsent(Identity.of(dependency.coordinates()), ignored -> new ArrayList<>()).add(dependency);

        for (var dependency : secondary.dependencies()) {
            List<ManifestDependency> existing = byPackage.get(Identity.of(dependency.coordinates()));
            if (existing == null) {
                dependencies.add(dependency);
            }
        }
        int added = dependencies.size() - primary.dependencies().size();
        int unresolved = Math.max(local.unresolvedCount(), sbom.unresolvedCount());
        int manifestCount = Math.max(local.manifestCount(), sbom.manifestCount());
        if (ecosystem == Ecosystem.MAVEN || unresolved > 0 || added > 0
                || "PARTIAL".equals(sbom.coverageStatus()) || "PARTIAL".equals(local.coverageStatus())) {
            String explanation = ecosystem == Ecosystem.MAVEN
                ? "Cobertura Maven parcial: se contrastaron CycloneDX y los POM locales. Las transitivas requieren evidencia verificable."
                : "Se contrastaron CycloneDX y los lockfiles. Hay componentes por confirmar.";
            return new ManifestAnalysis(dependencies, manifestCount, unresolved, "PARTIAL", explanation);
        }
        return new ManifestAnalysis(dependencies, manifestCount, 0);
    }

    private static void copyBounded(InputStream archive, Path replay) throws IOException {
        try (var output = Files.newOutputStream(replay)) {
            byte[] buffer = new byte[32 * 1024];
            long total = 0;
            for (int count; (count = archive.read(buffer)) != -1;) {
                total += count;
                if (total > MAX_REPLAY_ZIP_BYTES)
                    throw new IllegalArgumentException("El ZIP supera el límite de lectura del scanner.");
                output.write(buffer, 0, count);
            }
        }
    }

    private record Identity(String ecosystem, String namespace, String name, String version) {
        static Identity of(PackageCoordinates item) {
            return new Identity(item.ecosystem(), item.namespace(), item.name(), item.version());
        }
    }
}
