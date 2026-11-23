package com.dependencyintelligence.assistant.application.port.in;

import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.IndexResult;
import java.util.List;
import java.util.UUID;

public interface IndexDocuments { IndexResult execute(UUID organizationId, List<UUID> repositoryIds); }
