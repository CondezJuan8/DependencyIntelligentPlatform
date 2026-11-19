package com.dependencyintelligence.scan.application.port.in;
import com.dependencyintelligence.scan.application.dto.MavenInventory;
import java.util.Optional;
import java.util.UUID;
public interface AnalyzeNpmRepository { MavenInventory run(UUID organizationId,UUID repositoryId); Optional<MavenInventory> latest(UUID organizationId,UUID repositoryId); }
