package com.deroyqueit.productactivation.service;

import com.deroyqueit.productactivation.client.ActivationProviderClient;
import com.deroyqueit.productactivation.client.ActivationResult;
import com.deroyqueit.productactivation.client.ActivationResult.PermanentFailure;
import com.deroyqueit.productactivation.client.ActivationResult.RetryableFailure;
import com.deroyqueit.productactivation.client.ActivationResult.Success;
import com.deroyqueit.productactivation.config.properties.ProcessingProperties;
import com.deroyqueit.productactivation.domain.model.Activation;
import com.deroyqueit.productactivation.domain.model.ActivationAttempt;
import com.deroyqueit.productactivation.filter.CorrelationIdFilter;
import com.deroyqueit.productactivation.mapper.ActivationAttemptMapper;
import com.deroyqueit.productactivation.mapper.ActivationMapper;
import com.deroyqueit.productactivation.repository.ActivationAttemptRepository;
import com.deroyqueit.productactivation.repository.ActivationRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ActivationProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ActivationProcessingService.class);
    private static final int MAX_BACKOFF_EXPONENT = 20;

    private final ActivationRepository repository;
    private final ActivationAttemptRepository attemptRepository;
    private final ActivationProviderClient providerClient;
    private final ProcessingProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore slots;

    public ActivationProcessingService(ActivationRepository repository,
                                       ActivationAttemptRepository attemptRepository,
                                       ActivationProviderClient providerClient,
                                       ProcessingProperties properties,
                                       TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.attemptRepository = attemptRepository;
        this.providerClient = providerClient;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.slots = new Semaphore(properties.maxConcurrency());
    }

    public void processPendingActivations() {
        int free = slots.availablePermits();
        if (free == 0) {
            return;
        }
        claimPendingActivations(free).forEach(activation -> {
            slots.acquireUninterruptibly();
            executor.submit(() -> {
                try {
                    activateSafely(activation);
                } finally {
                    slots.release();
                }
            });
        });
    }

    public void recoverStuckActivations() {
        transactionTemplate.executeWithoutResult(transaction -> {
            Instant threshold = Instant.now().minus(properties.stuckTimeout());
            repository.lockProcessingStartedBefore(threshold, properties.batchSize()).stream()
                    .map(ActivationMapper::toDomain)
                    .forEach(activation -> finishAttempt(activation,
                            new RetryableFailure("Interrupted before provider response", null)));
        });
    }

    @PreDestroy
    void shutdown() {
        executor.close();
    }

    private List<Activation> claimPendingActivations(int limit) {
        return transactionTemplate.execute(transaction ->
                repository.claimPendingDueBy(Instant.now(), limit).stream()
                        .map(ActivationMapper::toDomain)
                        .toList());
    }

    private void activateSafely(Activation activation) {
        MDC.put(CorrelationIdFilter.MDC_KEY, activation.id().toString());
        try {
            activate(activation);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Activation changed concurrently, result discarded");
        } catch (RuntimeException e) {
            log.error("Unexpected error while activating, it will be recovered later", e);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private void activate(Activation activation) {
        ActivationResult result = providerClient.activate(activation);
        transactionTemplate.executeWithoutResult(transaction -> finishAttempt(activation, result));
    }

    private void finishAttempt(Activation activation, ActivationResult result) {
        int attemptNumber = Math.toIntExact(attemptRepository.countByActivationId(activation.id()) + 1);

        Activation updated = switch (result) {
            case Success success -> activation.complete();
            case PermanentFailure failure -> activation.fail();
            case RetryableFailure(String reason, Duration retryAfter) ->
                    retryOrFail(activation, attemptNumber, retryAfter);
        };
        attemptRepository.save(ActivationAttemptMapper.toEntity(
                ActivationAttempt.create(activation.id(), attemptNumber, errorOf(result))));
        save(updated);
        log.info("Activation attempt {} finished with status {}", attemptNumber, updated.status());
    }

    private Activation retryOrFail(Activation activation, int attemptNumber, Duration retryAfter) {
        if (attemptNumber >= properties.maxAttempts()) {
            return activation.fail();
        }
        Duration delay = retryAfter != null ? atMostMaxBackoff(retryAfter) : backoffFor(attemptNumber);
        return activation.retryLater(delay);
    }

    private Duration backoffFor(int attempts) {
        int exponent = Math.min(attempts - 1, MAX_BACKOFF_EXPONENT);
        Duration backoff = properties.initialBackoff().multipliedBy(1L << exponent);
        return withJitter(atMostMaxBackoff(backoff));
    }

    private Duration atMostMaxBackoff(Duration delay) {
        return delay.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : delay;
    }

    private static Duration withJitter(Duration backoff) {
        double factor = ThreadLocalRandom.current().nextDouble(0.5, 1.0);
        return Duration.ofMillis((long) (backoff.toMillis() * factor));
    }

    private static String errorOf(ActivationResult result) {
        return switch (result) {
            case Success success -> null;
            case PermanentFailure(String reason) -> reason;
            case RetryableFailure(String reason, Duration retryAfter) -> reason;
        };
    }

    private Activation save(Activation activation) {
        return ActivationMapper.toDomain(repository.saveAndFlush(ActivationMapper.toEntity(activation)));
    }
}
