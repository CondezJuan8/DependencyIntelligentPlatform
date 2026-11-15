package com.dependencyintelligence.identity.application.dto;

public record AuthenticatedAccount(AccountResult account, long credentialVersion) { }
