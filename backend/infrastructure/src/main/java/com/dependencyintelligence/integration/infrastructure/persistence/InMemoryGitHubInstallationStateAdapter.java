package com.dependencyintelligence.integration.infrastructure.persistence;

import com.dependencyintelligence.integration.application.port.out.GitHubInstallationStateStore;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryGitHubInstallationStateAdapter implements GitHubInstallationStateStore {
    private final ConcurrentHashMap<String, Pending> states = new ConcurrentHashMap<>();
    @Override public void save(String state, Pending pending) {
        Instant now = Instant.now();
        states.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        states.put(state, pending);
    }
    @Override public Optional<Pending> find(String state) { return Optional.ofNullable(states.get(state)); }
    @Override public boolean consume(String state, Pending pending) { return states.remove(state, pending); }
    @Override public boolean attachAuthorization(String state, Pending current, Pending authorized) { return states.replace(state, current, authorized); }
}
