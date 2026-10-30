package com.deroyqueit.productactivation.service;

import com.deroyqueit.productactivation.IntegrationTest;
import com.deroyqueit.productactivation.client.ActivationProviderClient;
import com.deroyqueit.productactivation.client.ActivationResult.PermanentFailure;
import com.deroyqueit.productactivation.client.ActivationResult.RetryableFailure;
import com.deroyqueit.productactivation.client.ActivationResult.Success;
import com.deroyqueit.productactivation.config.properties.ProcessingProperties;
import com.deroyqueit.productactivation.domain.model.Activation;
import com.deroyqueit.productactivation.domain.model.ActivationAttempt;
import com.deroyqueit.productactivation.domain.model.ActivationStatus;
import com.deroyqueit.productactivation.infrastructure.ActivationEntity;
import com.deroyqueit.productactivation.mapper.ActivationAttemptMapper;
import com.deroyqueit.productactivation.repository.ActivationAttemptRepository;
import com.deroyqueit.productactivation.repository.ActivationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@IntegrationTest
class ActivationProcessingServiceTests {

    private static final String CLIENT = "client-1";
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(30);

    @MockitoBean
    private ActivationProviderClient providerClient;

    @Autowired
    private ActivationProcessingService processingService;

    @Autowired
    private ActivationService activationService;

    @Autowired
    private ActivationRepository repository;

    @Autowired
    private ActivationAttemptRepository attemptRepository;

    @Autowired
    private ProcessingProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void providerSuccessCompletesActivation() {
        UUID id = createActivation();
        given(providerClient.activate(any())).willReturn(new Success());

        processAndAwait();

        assertThat(activationService.findById(CLIENT, id).status()).isEqualTo(ActivationStatus.COMPLETED);
        assertThat(attemptsOf(id))
                .singleElement()
                .satisfies(attempt -> {
                    assertThat(attempt.attemptNumber()).isEqualTo(1);
                    assertThat(attempt.error()).isNull();
                });
    }

    @Test
    void providerRejectionFailsActivationWithoutRetry() {
        UUID id = createActivation();
        given(providerClient.activate(any())).willReturn(new PermanentFailure("Provider responded HTTP 400"));

        processAndAwait();

        assertThat(activationService.findById(CLIENT, id).status()).isEqualTo(ActivationStatus.FAILED);
        assertThat(attemptsOf(id))
                .singleElement()
                .extracting(ActivationAttempt::error)
                .isEqualTo("Provider responded HTTP 400");
    }

    @Test
    void temporaryFailureSchedulesRetryWithJitteredBackoff() {
        UUID id = createActivation();
        given(providerClient.activate(any())).willReturn(new RetryableFailure("Provider responded HTTP 500", null));
        Instant before = Instant.now();

        processAndAwait();

        Activation activation = activationService.findById(CLIENT, id);
        assertThat(activation.status()).isEqualTo(ActivationStatus.PENDING);
        assertThat(activation.nextAttemptAt())
                .isAfterOrEqualTo(before.plus(INITIAL_BACKOFF.dividedBy(2)))
                .isBeforeOrEqualTo(Instant.now().plus(INITIAL_BACKOFF));
        assertThat(attemptsOf(id))
                .singleElement()
                .extracting(ActivationAttempt::error)
                .isEqualTo("Provider responded HTTP 500");
    }

    @Test
    void retryAfterLongerThanMaxBackoffIsCapped() {
        UUID id = createActivation();
        given(providerClient.activate(any())).willReturn(new RetryableFailure("Provider responded HTTP 429", Duration.ofDays(1)));

        processAndAwait();

        assertThat(activationService.findById(CLIENT, id).nextAttemptAt())
                .isBeforeOrEqualTo(Instant.now().plus(properties.maxBackoff()));
    }

    @Test
    void activationFailsWhenMaxAttemptsIsReached() {
        UUID id = createActivation();
        given(providerClient.activate(any())).willReturn(new RetryableFailure("Provider unreachable", Duration.ZERO));

        processAndAwait();
        processAndAwait();

        assertThat(activationService.findById(CLIENT, id).status()).isEqualTo(ActivationStatus.FAILED);
        assertThat(attemptsOf(id))
                .extracting(ActivationAttempt::attemptNumber)
                .containsExactly(1, 2);
    }

    @Test
    void activationsOfTheSameBatchCallTheProviderInParallel() {
        List<UUID> ids = List.of(createActivation(), createActivation(), createActivation(), createActivation());
        CountDownLatch allCallsStarted = new CountDownLatch(ids.size());
        given(providerClient.activate(any())).willAnswer(invocation -> {
            allCallsStarted.countDown();
            return allCallsStarted.await(5, TimeUnit.SECONDS) ? new Success() : new PermanentFailure("Calls ran one by one");
        });

        processAndAwait();

        ids.forEach(id ->
                assertThat(activationService.findById(CLIENT, id).status()).isEqualTo(ActivationStatus.COMPLETED));
    }

    @Test
    void slowActivationDoesNotHoldTheNextCycle() {
        UUID slow = createActivation();
        CountDownLatch releaseSlow = new CountDownLatch(1);
        given(providerClient.activate(any())).willAnswer(invocation -> {
            Activation activation = invocation.getArgument(0);
            if (activation.id().equals(slow)) {
                releaseSlow.await(5, TimeUnit.SECONDS);
            }
            return new Success();
        });
        processingService.processPendingActivations();

        UUID fast = createActivation();
        processingService.processPendingActivations();

        await().untilAsserted(() ->
                assertThat(activationService.findById(CLIENT, fast).status()).isEqualTo(ActivationStatus.COMPLETED));
        assertThat(activationService.findById(CLIENT, slow).status()).isEqualTo(ActivationStatus.PROCESSING);

        releaseSlow.countDown();
        await().untilAsserted(() ->
                assertThat(activationService.findById(CLIENT, slow).status()).isEqualTo(ActivationStatus.COMPLETED));
    }

    @Test
    void activationStuckInProcessingIsRecovered() {
        UUID id = createActivation();
        given(providerClient.activate(any())).willThrow(new IllegalStateException("simulated crash"));

        processingService.processPendingActivations();
        verify(providerClient, timeout(5000)).activate(any());
        assertThat(activationService.findById(CLIENT, id).status()).isEqualTo(ActivationStatus.PROCESSING);
        assertThat(attemptsOf(id)).isEmpty();

        jdbcTemplate.update("UPDATE activation SET updated_at = updated_at - INTERVAL '1 hour' WHERE id = ?", id);
        processingService.recoverStuckActivations();

        assertThat(activationService.findById(CLIENT, id).status()).isEqualTo(ActivationStatus.PENDING);
        assertThat(attemptsOf(id))
                .singleElement()
                .extracting(ActivationAttempt::error)
                .isEqualTo("Interrupted before provider response");
    }

    @Test
    void anotherInstanceSkipsPendingActivationsAlreadyLocked() {
        createActivation();
        createActivation();

        transactionTemplate.executeWithoutResult(firstInstance -> {
            List<ActivationEntity> lockedByFirst = repository.claimPendingDueBy(Instant.now(), 1);

            List<ActivationEntity> lockedBySecond = CompletableFuture
                    .supplyAsync(() -> transactionTemplate.execute(secondInstance ->
                            repository.claimPendingDueBy(Instant.now(), 10)))
                    .orTimeout(5, TimeUnit.SECONDS)
                    .join();

            assertThat(lockedByFirst).hasSize(1);
            assertThat(lockedBySecond)
                    .extracting(ActivationEntity::getId)
                    .hasSize(1)
                    .doesNotContain(lockedByFirst.getFirst().getId());
        });
    }

    private void processAndAwait() {
        processingService.processPendingActivations();
        await().until(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM activation WHERE status = 'PROCESSING'", Long.class) == 0);
    }

    private UUID createActivation() {
        return activationService.create(CLIENT, "EXT-" + UUID.randomUUID(), "CUST-1", "INTERNET-500").id();
    }

    private List<ActivationAttempt> attemptsOf(UUID id) {
        return attemptRepository.findByActivationIdOrderByAttemptNumber(id).stream()
                .map(ActivationAttemptMapper::toDomain)
                .toList();
    }
}
