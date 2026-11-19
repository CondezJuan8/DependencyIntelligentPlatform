package com.dependencyintelligence.identity.application.port.in;

import com.dependencyintelligence.identity.application.dto.BootstrapPlatformAdministratorCommand;
import java.util.UUID;

public interface BootstrapPlatformAdministrator {
    UUID execute(BootstrapPlatformAdministratorCommand command);
}
