package com.deroyqueit.productactivation.client;

import com.deroyqueit.productactivation.domain.model.Activation;

import java.util.UUID;

record ProviderActivationPayload(UUID activationId, String externalId, String customerId, String productCode) {

    static ProviderActivationPayload from(Activation activation) {
        return new ProviderActivationPayload(
                activation.id(),
                activation.externalId(),
                activation.customerId(),
                activation.productCode()
        );
    }
}
