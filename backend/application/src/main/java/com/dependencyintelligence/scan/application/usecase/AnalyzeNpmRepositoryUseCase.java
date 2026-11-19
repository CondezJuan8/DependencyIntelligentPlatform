package com.dependencyintelligence.scan.application.usecase;
import com.dependencyintelligence.scan.application.dto.MavenInventory;
import com.dependencyintelligence.scan.application.port.in.AnalyzeNpmRepository;
import com.dependencyintelligence.scan.application.port.out.*;
import java.io.IOException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
public final class AnalyzeNpmRepositoryUseCase implements AnalyzeNpmRepository {
 private final RepositoryAnalysisSource sources;private final NpmLockfileReader reader;private final MavenAnalysisStore store;private final Clock clock;
 public AnalyzeNpmRepositoryUseCase(RepositoryAnalysisSource s,NpmLockfileReader r,MavenAnalysisStore st,Clock c){sources=s;reader=r;store=st;clock=c;}
 public MavenInventory run(UUID org,UUID repo){try(var source=sources.open(org,repo)){var data=reader.read(source.content());if(data.manifestCount()==0)throw new IllegalArgumentException("No se encontró package-lock.json compatible.");return store.save("npm-lockfile",UUID.randomUUID(),org,repo,source.provider(),source.sha256(),source.commitSha(),clock.instant().truncatedTo(ChronoUnit.MICROS),data);}catch(IOException e){throw new IllegalStateException("No se pudo cerrar el código fuente.",e);}}
 public Optional<MavenInventory> latest(UUID org,UUID repo){return store.latest("npm-lockfile",org,repo);}
}
