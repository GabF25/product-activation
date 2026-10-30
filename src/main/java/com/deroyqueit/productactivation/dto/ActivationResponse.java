package com.deroyqueit.productactivation.dto;

import com.deroyqueit.productactivation.domain.model.Activation;
import com.deroyqueit.productactivation.domain.model.ActivationStatus;

import java.time.Instant;
import java.util.UUID;

public record ActivationResponse(
        UUID id,
        String externalId,
        String customerId,
        String productCode,
        ActivationStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ActivationResponse from(Activation activation) {
        return new ActivationResponse(
                activation.id(),
                activation.externalId(),
                activation.customerId(),
                activation.productCode(),
                activation.status(),
                activation.createdAt(),
                activation.updatedAt()
        );
    }
}
