package com.deroyqueit.productactivation.domain.model;

import com.deroyqueit.productactivation.domain.exception.InvalidStatusTransitionException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.deroyqueit.productactivation.domain.model.ActivationStatus.COMPLETED;
import static com.deroyqueit.productactivation.domain.model.ActivationStatus.FAILED;
import static com.deroyqueit.productactivation.domain.model.ActivationStatus.PENDING;
import static com.deroyqueit.productactivation.domain.model.ActivationStatus.PROCESSING;

public record Activation(
        UUID id,
        String clientId,
        String externalId,
        String customerId,
        String productCode,
        ActivationStatus status,
        Instant nextAttemptAt,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static Activation create(String clientId, String externalId, String customerId, String productCode) {
        return new Activation(UUID.randomUUID(), clientId, externalId, customerId, productCode,
                PENDING, Instant.now(), null, null, null);
    }

    public Activation startProcessing() {
        ensureCanTransitionTo(PROCESSING);
        return withStatus(PROCESSING, null);
    }

    public Activation complete() {
        ensureCanTransitionTo(COMPLETED);
        return withStatus(COMPLETED, null);
    }

    public Activation fail() {
        ensureCanTransitionTo(FAILED);
        return withStatus(FAILED, null);
    }

    public Activation retryLater(Duration delay) {
        ensureCanTransitionTo(PENDING);
        return withStatus(PENDING, Instant.now().plus(delay));
    }

    public boolean hasSameContent(String customerId, String productCode) {
        return this.customerId.equals(customerId) && this.productCode.equals(productCode);
    }

    private Activation withStatus(ActivationStatus status, Instant nextAttemptAt) {
        return new Activation(id, clientId, externalId, customerId, productCode,
                status, nextAttemptAt, version, createdAt, updatedAt);
    }

    private void ensureCanTransitionTo(ActivationStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(status, target);
        }
    }
}
