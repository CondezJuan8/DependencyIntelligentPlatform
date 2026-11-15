package com.dependencyintelligence.identity.infrastructure.configuration;

import com.dependencyintelligence.identity.application.dto.ResetPasswordCommand;
import com.dependencyintelligence.identity.application.port.in.RecoverAccount;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

public class TransactionalAccountRecovery implements RecoverAccount {
    private final RecoverAccount delegate;
    private final TransactionTemplate transactions;

    public TransactionalAccountRecovery(RecoverAccount delegate, PlatformTransactionManager manager) {
        this.delegate = delegate;
        this.transactions = new TransactionTemplate(manager);
    }

    @Override public void requestVerification(UUID id) { transactions.executeWithoutResult(status -> delegate.requestVerification(id)); }
    @Override public void verifyEmail(String token) { transactions.executeWithoutResult(status -> delegate.verifyEmail(token)); }
    @Override public void requestPasswordReset(String email) { transactions.executeWithoutResult(status -> delegate.requestPasswordReset(email)); }
    @Override public void resetPassword(ResetPasswordCommand command) { transactions.executeWithoutResult(status -> delegate.resetPassword(command)); }
}
