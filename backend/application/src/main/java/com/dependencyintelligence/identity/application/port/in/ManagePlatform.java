package com.dependencyintelligence.identity.application.port.in;
import com.dependencyintelligence.identity.application.dto.PlatformModels.*;
import java.util.UUID;
public interface ManagePlatform {
    Overview overview(UUID actor);
    void execute(UUID actor,Action action);
    void updateName(UUID user,String name);
}
