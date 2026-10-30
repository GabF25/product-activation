package com.deroyqueit.productactivation.repository;

import com.deroyqueit.productactivation.infrastructure.ActivationAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivationAttemptRepository extends JpaRepository<ActivationAttemptEntity, UUID> {

    long countByActivationId(UUID activationId);

    List<ActivationAttemptEntity> findByActivationIdOrderByAttemptNumber(UUID activationId);
}
