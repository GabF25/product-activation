package com.deroyqueit.productactivation.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ActivationAttempt(
        UUID id,
        UUID activationId,
        int attemptNumber,
        Instant attemptedAt,
        String error
) {

    public static ActivationAttempt create(UUID activationId, int attemptNumber, String error) {
        return new ActivationAttempt(UUID.randomUUID(), activationId, attemptNumber, Instant.now(), error);
    }
}
