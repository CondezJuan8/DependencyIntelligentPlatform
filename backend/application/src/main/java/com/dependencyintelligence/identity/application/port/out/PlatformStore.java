package com.dependencyintelligence.identity.application.port.out;
import com.dependencyintelligence.identity.application.dto.PlatformModels.*;
import java.util.UUID;
public interface PlatformStore {
    boolean administrator(UUID user);
    void lockAdministration();
    Overview overview();
    User user(UUID id);
    long activeAdministrators();
    void change(UUID actor,Action action);
    void audit(UUID actor,Action action);
    void updateName(UUID user,String name);
}
