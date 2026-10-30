package com.deroyqueit.productactivation.mapper;

import com.deroyqueit.productactivation.domain.model.ActivationAttempt;
import com.deroyqueit.productactivation.infrastructure.ActivationAttemptEntity;

public final class ActivationAttemptMapper {

    private ActivationAttemptMapper() {
    }

    public static ActivationAttempt toDomain(ActivationAttemptEntity entity) {
        return new ActivationAttempt(
                entity.getId(),
                entity.getActivationId(),
                entity.getAttemptNumber(),
                entity.getAttemptedAt(),
                entity.getError()
        );
    }

    public static ActivationAttemptEntity toEntity(ActivationAttempt domain) {
        return ActivationAttemptEntity.builder()
                .id(domain.id())
                .activationId(domain.activationId())
                .attemptNumber(domain.attemptNumber())
                .attemptedAt(domain.attemptedAt())
                .error(domain.error())
                .build();
    }
}
