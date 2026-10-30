package com.deroyqueit.productactivation.service;

import com.deroyqueit.productactivation.domain.exception.ActivationNotFoundException;
import com.deroyqueit.productactivation.domain.exception.ExternalIdConflictException;
import com.deroyqueit.productactivation.domain.model.Activation;
import com.deroyqueit.productactivation.domain.model.ActivationStatus;
import com.deroyqueit.productactivation.infrastructure.ActivationEntity;
import com.deroyqueit.productactivation.mapper.ActivationMapper;
import com.deroyqueit.productactivation.repository.ActivationRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ActivationService {

    private static final Logger log = LoggerFactory.getLogger(ActivationService.class);
    private static final String EXTERNAL_ID_CONSTRAINT = "uk_activation_client_external_id";

    private final ActivationRepository repository;

    public ActivationService(ActivationRepository repository) {
        this.repository = repository;
    }

    public Activation create(String clientId, String externalId, String customerId, String productCode) {
        Activation activation = Activation.create(clientId, externalId, customerId, productCode);
        return insert(activation).orElseGet(() -> existingOrConflict(activation));
    }

    public Activation findById(String clientId, UUID id) {
        return repository.findByIdAndClientId(id, clientId)
                .map(ActivationMapper::toDomain)
                .orElseThrow(() -> new ActivationNotFoundException(id));
    }

    public Page<Activation> list(String clientId, ActivationStatus status, String customerId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.search(clientId, status, customerId, pageRequest)
                .map(ActivationMapper::toDomain);
    }

    private Optional<Activation> insert(Activation activation) {
        try {
            ActivationEntity saved = repository.saveAndFlush(ActivationMapper.toEntity(activation));
            log.info("Activation {} created", saved.getId());
            return Optional.of(ActivationMapper.toDomain(saved));
        } catch (DataIntegrityViolationException e) {
            if (isExternalIdViolation(e)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private Activation existingOrConflict(Activation attempted) {
        Activation existing = repository.findByClientIdAndExternalId(attempted.clientId(), attempted.externalId())
                .map(ActivationMapper::toDomain)
                .orElseThrow(() -> new IllegalStateException("Duplicate externalId not found: " + attempted.externalId()));

        if (!existing.hasSameContent(attempted.customerId(), attempted.productCode())) {
            throw new ExternalIdConflictException(attempted.externalId());
        }
        return existing;
    }

    private static boolean isExternalIdViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return EXTERNAL_ID_CONSTRAINT.equals(violation.getConstraintName());
            }
        }
        return false;
    }
}
