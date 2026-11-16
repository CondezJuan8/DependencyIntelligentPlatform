package com.dependencyintelligence.identity.application.dto;

public record ExternalAccountState(AuthenticatedAccount authenticated, boolean active, boolean passwordAvailable) { }
