package com.dependencyintelligence.identity.application.port.in;

import com.dependencyintelligence.identity.application.dto.AccountResult;
import java.util.UUID;

public interface FindActiveAccount {
    AccountResult execute(UUID userId);
}
