package com.dependencyintelligence.identity.application.port.in;

import com.dependencyintelligence.identity.application.dto.AccountResult;
import com.dependencyintelligence.identity.application.dto.RegisterAccountCommand;

public interface RegisterAccount {
    AccountResult execute(RegisterAccountCommand command);
}
