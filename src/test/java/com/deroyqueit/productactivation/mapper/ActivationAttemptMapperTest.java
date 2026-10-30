package com.deroyqueit.productactivation.mapper;

import com.deroyqueit.productactivation.domain.model.ActivationAttempt;
import com.deroyqueit.productactivation.infrastructure.ActivationAttemptEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActivationAttemptMapperTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID ACTIVATION_ID = UUID.randomUUID();
    private static final Instant ATTEMPTED_AT = Instant.parse("2026-10-26T14:05:00Z");

    @Test
    void mapsEveryFieldFromDomainToEntity() {
        ActivationAttemptEntity entity = ActivationAttemptMapper.toEntity(domain());

        assertThat(entity.getId()).isEqualTo(ID);
        assertThat(entity.getActivationId()).isEqualTo(ACTIVATION_ID);
        assertThat(entity.getAttemptNumber()).isEqualTo(3);
        assertThat(entity.getAttemptedAt()).isEqualTo(ATTEMPTED_AT);
        assertThat(entity.getError()).isEqualTo("HTTP 500");
    }

    @Test
    void mapsEveryFieldFromEntityToDomain() {
        ActivationAttemptEntity entity = ActivationAttemptEntity.builder()
                .id(ID)
                .activationId(ACTIVATION_ID)
                .attemptNumber(3)
                .attemptedAt(ATTEMPTED_AT)
                .error("HTTP 500")
                .build();

        assertThat(ActivationAttemptMapper.toDomain(entity)).isEqualTo(domain());
    }

    private static ActivationAttempt domain() {
        return new ActivationAttempt(ID, ACTIVATION_ID, 3, ATTEMPTED_AT, "HTTP 500");
    }
}
