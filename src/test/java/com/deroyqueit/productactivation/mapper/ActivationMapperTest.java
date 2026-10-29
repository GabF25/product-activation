package com.deroyqueit.productactivation.mapper;

import com.deroyqueit.productactivation.domain.model.Activation;
import com.deroyqueit.productactivation.domain.model.ActivationStatus;
import com.deroyqueit.productactivation.infrastructure.ActivationEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActivationMapperTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant NEXT_ATTEMPT_AT = Instant.parse("2026-10-26T14:05:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-10-26T14:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-10-26T14:03:00Z");

    @Test
    void mapsEveryFieldFromDomainToEntity() {
        ActivationEntity entity = ActivationMapper.toEntity(domain());

        assertThat(entity.getId()).isEqualTo(ID);
        assertThat(entity.getClientId()).isEqualTo("client-1");
        assertThat(entity.getExternalId()).isEqualTo("EXT-1");
        assertThat(entity.getCustomerId()).isEqualTo("CUST-1");
        assertThat(entity.getProductCode()).isEqualTo("INTERNET-500");
        assertThat(entity.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(entity.getNextAttemptAt()).isEqualTo(NEXT_ATTEMPT_AT);
        assertThat(entity.getVersion()).isEqualTo(7L);
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(entity.getUpdatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void mapsEveryFieldFromEntityToDomain() {
        ActivationEntity entity = ActivationEntity.builder()
                .id(ID)
                .clientId("client-1")
                .externalId("EXT-1")
                .customerId("CUST-1")
                .productCode("INTERNET-500")
                .status(ActivationStatus.PENDING)
                .nextAttemptAt(NEXT_ATTEMPT_AT)
                .version(7L)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();

        assertThat(ActivationMapper.toDomain(entity)).isEqualTo(domain());
    }

    private static Activation domain() {
        return new Activation(ID, "client-1", "EXT-1", "CUST-1", "INTERNET-500", ActivationStatus.PENDING,
                NEXT_ATTEMPT_AT, 7L, CREATED_AT, UPDATED_AT);
    }
}
