package com.dependencyintelligence.identity.application.port.in;
import java.util.List;
import java.util.UUID;
public interface FindAccountWorkspace {
    record Repository(UUID id,UUID organizationId,String organizationName,String fullName,String defaultBranch,String source){}
    List<Repository> repositories(UUID user);
}
