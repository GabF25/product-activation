package com.deroyqueit.productactivation.mapper;

import com.deroyqueit.productactivation.domain.model.Activation;
import com.deroyqueit.productactivation.infrastructure.ActivationEntity;

public final class ActivationMapper {

    private ActivationMapper() {
    }

    public static Activation toDomain(ActivationEntity entity) {
        return new Activation(
                entity.getId(),
                entity.getClientId(),
                entity.getExternalId(),
                entity.getCustomerId(),
                entity.getProductCode(),
                entity.getStatus(),
                entity.getNextAttemptAt(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static ActivationEntity toEntity(Activation domain) {
        return ActivationEntity.builder()
                .id(domain.id())
                .clientId(domain.clientId())
                .externalId(domain.externalId())
                .customerId(domain.customerId())
                .productCode(domain.productCode())
                .status(domain.status())
                .nextAttemptAt(domain.nextAttemptAt())
                .version(domain.version())
                .createdAt(domain.createdAt())
                .updatedAt(domain.updatedAt())
                .build();
    }
}
