package com.dependencyintelligence.scan.application.dto;

import java.util.UUID;

public record AnalysisJobTicket(UUID jobId, String status) { }
