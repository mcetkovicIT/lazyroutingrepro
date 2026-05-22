package com.example.lazyroutingrepro.probe;

import java.time.Instant;

public record ProbeResponse(
    String mode,
    String marker,
    String currentDatabase,
    String nodeRole,
    boolean connectionReadOnly,
    int writeAuditCount,
    Instant observedAt
) {
}