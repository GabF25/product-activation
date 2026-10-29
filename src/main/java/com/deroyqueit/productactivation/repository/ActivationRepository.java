package com.deroyqueit.productactivation.repository;

import com.deroyqueit.productactivation.domain.model.ActivationStatus;
import com.deroyqueit.productactivation.infrastructure.ActivationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ActivationRepository extends JpaRepository<ActivationEntity, UUID> {

    Optional<ActivationEntity> findByIdAndClientId(UUID id, String clientId);

    Optional<ActivationEntity> findByClientIdAndExternalId(String clientId, String externalId);

    @Query("""
            SELECT r FROM ActivationEntity r
            WHERE r.clientId = :clientId
              AND (:status IS NULL OR r.status = :status)
              AND (:customerId IS NULL OR r.customerId = :customerId)
            """)
    Page<ActivationEntity> search(@Param("clientId") String clientId,
                                  @Param("status") ActivationStatus status,
                                  @Param("customerId") String customerId,
                                  Pageable pageable);
}
