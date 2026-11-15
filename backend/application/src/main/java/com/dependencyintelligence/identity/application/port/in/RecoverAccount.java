package com.dependencyintelligence.identity.application.port.in;

import com.dependencyintelligence.identity.application.dto.ResetPasswordCommand;
import java.util.UUID;

public interface RecoverAccount {
    void requestVerification(UUID userId);
    void verifyEmail(String token);
    void requestPasswordReset(String email);
    void resetPassword(ResetPasswordCommand command);
}
