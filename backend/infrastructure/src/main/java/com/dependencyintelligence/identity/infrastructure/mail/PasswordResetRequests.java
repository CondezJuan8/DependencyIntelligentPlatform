package com.dependencyintelligence.identity.infrastructure.mail;

import com.dependencyintelligence.identity.application.port.in.RecoverAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

/** Account lookup and SMTP happen after the uniform public response has been accepted. */
@Component
public class PasswordResetRequests {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetRequests.class);
    private final RecoverAccount recovery;
    private final ThreadPoolTaskExecutor executor;

    public PasswordResetRequests(RecoverAccount recovery) {
        this.recovery = recovery;
        this.executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("account-recovery-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
    }

    public void request(String email) {
        executor.execute(() -> {
            try { recovery.requestPasswordReset(email); }
            catch (RuntimeException exception) {
                LOG.warn("account_password_reset_delivery_failed type={}", exception.getClass().getSimpleName());
            }
        });
    }

    @PreDestroy public void close() { executor.shutdown(); }
}
