package com.dependencyintelligence.identity.application.usecase;

import com.dependencyintelligence.identity.application.dto.RecoveryRecipient;
import com.dependencyintelligence.identity.application.dto.ResetPasswordCommand;
import com.dependencyintelligence.identity.application.exception.InvalidAccountTokenException;
import com.dependencyintelligence.identity.application.port.in.RecoverAccount;
import com.dependencyintelligence.identity.application.port.out.*;
import com.dependencyintelligence.identity.domain.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class RecoverAccountUseCase implements RecoverAccount {
    private final AccountRecoveryStore store;
    private final AccountMail mail;
    private final AccountTokenCodec tokens;
    private final PasswordHashing passwords;
    private final Clock clock;
    private final Duration verificationLifetime;
    private final Duration resetLifetime;
    private final Duration cooldown;

    public RecoverAccountUseCase(AccountRecoveryStore store, AccountMail mail, AccountTokenCodec tokens,
            PasswordHashing passwords, Clock clock, Duration verificationLifetime, Duration resetLifetime, Duration cooldown) {
        if (verificationLifetime.isNegative() || verificationLifetime.isZero() || resetLifetime.isNegative()
                || resetLifetime.isZero() || cooldown.isNegative()) throw new IllegalArgumentException("Invalid token durations");
        this.store = store;
        this.mail = mail;
        this.tokens = tokens;
        this.passwords = passwords;
        this.clock = clock;
        this.verificationLifetime = verificationLifetime;
        this.resetLifetime = resetLifetime;
        this.cooldown = cooldown;
    }

    @Override public void requestVerification(UUID userId) {
        store.lockUser(userId).filter(recipient -> !recipient.emailVerified())
            .ifPresent(recipient -> issue(recipient, AccountTokenPurpose.VERIFY_EMAIL, verificationLifetime));
    }

    @Override public void requestPasswordReset(String email) {
        String normalized;
        try { normalized = new EmailAddress(email).value(); }
        catch (IllegalArgumentException exception) { return; }
        store.lockUserByEmail(normalized).filter(RecoveryRecipient::localCredentials)
            .ifPresent(recipient -> issue(recipient, AccountTokenPurpose.RESET_PASSWORD, resetLifetime));
    }

    private void issue(RecoveryRecipient recipient, AccountTokenPurpose purpose, Duration lifetime) {
        Instant now = clock.instant();
        if (store.issuedSince(recipient.id(), purpose, now.minus(cooldown))) return;
        String token = tokens.generate();
        store.consume(recipient.id(), purpose, now);
        store.issue(recipient.id(), purpose, tokens.fingerprint(token), now, now.plus(lifetime));
        mail.sendLink(recipient.email(), purpose, token);
    }

    @Override public void verifyEmail(String token) {
        Instant now = clock.instant();
        var recipient = store.lockTokenOwner(tokens.fingerprint(token), AccountTokenPurpose.VERIFY_EMAIL, now)
            .orElseThrow(InvalidAccountTokenException::new);
        store.markVerified(recipient.id(), now);
        store.consume(recipient.id(), AccountTokenPurpose.VERIFY_EMAIL, now);
    }

    @Override public void resetPassword(ResetPasswordCommand command) {
        PasswordPolicy.validate(command.password());
        Instant now = clock.instant();
        var recipient = store.lockTokenOwner(tokens.fingerprint(command.token()), AccountTokenPurpose.RESET_PASSWORD, now)
            .filter(RecoveryRecipient::localCredentials).orElseThrow(InvalidAccountTokenException::new);
        store.replacePassword(recipient.id(), passwords.hash(command.password()), now);
        store.consume(recipient.id(), AccountTokenPurpose.RESET_PASSWORD, now);
    }
}
