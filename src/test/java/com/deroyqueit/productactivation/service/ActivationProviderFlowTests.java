package com.deroyqueit.productactivation.service;

import com.deroyqueit.productactivation.IntegrationTest;
import com.deroyqueit.productactivation.client.ActivationProviderClient;
import com.deroyqueit.productactivation.config.properties.ProcessingProperties;
import com.deroyqueit.productactivation.config.properties.ProviderProperties;
import com.deroyqueit.productactivation.domain.model.ActivationStatus;
import com.deroyqueit.productactivation.repository.ActivationAttemptRepository;
import com.deroyqueit.productactivation.repository.ActivationRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@IntegrationTest
class ActivationProviderFlowTests {

    private static final String CLIENT = "client-1";

    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);

    @Autowired
    private ActivationService activationService;

    @Autowired
    private ActivationRepository repository;

    @Autowired
    private ActivationAttemptRepository attemptRepository;

    @Autowired
    private ProcessingProperties processingProperties;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpServer provider;
    private ActivationProcessingService processingService;

    @BeforeEach
    void setUp() throws IOException {
        repository.deleteAll();

        provider = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        provider.start();
        String baseUrl = "http://localhost:" + provider.getAddress().getPort();
        ActivationProviderClient client =
                new ActivationProviderClient(new ProviderProperties(baseUrl, Duration.ofSeconds(1), READ_TIMEOUT));

        processingService =
                new ActivationProcessingService(repository, attemptRepository, client, processingProperties,
                        transactionTemplate);
    }

    @AfterEach
    void stopProvider() {
        provider.stop(0);
    }

    @Test
    void providerSuccessCompletesActivation() {
        UUID id = createActivation();
        providerResponds(200, Duration.ZERO);

        processAndAwait();

        assertThat(activationService.findById(CLIENT, id).status()).isEqualTo(ActivationStatus.COMPLETED);
    }

    @Test
    void providerRejectionFailsActivation() {
        UUID id = createActivation();
        providerResponds(400, Duration.ZERO);

        processAndAwait();

        assertThat(activationService.findById(CLIENT, id).status()).isEqualTo(ActivationStatus.FAILED);
        assertThat(lastError(id)).isEqualTo("Provider responded HTTP 400");
    }

    @Test
    void providerServerErrorSchedulesRetry() {
        UUID id = createActivation();
        providerResponds(500, Duration.ZERO);

        processAndAwait();

        assertThat(activationService.findById(CLIENT, id).status()).isEqualTo(ActivationStatus.PENDING);
        assertThat(lastError(id)).isEqualTo("Provider responded HTTP 500");
    }

    @Test
    void providerTimeoutSchedulesRetry() {
        UUID id = createActivation();
        providerResponds(200, READ_TIMEOUT.multipliedBy(3));

        processAndAwait();

        assertThat(activationService.findById(CLIENT, id).status()).isEqualTo(ActivationStatus.PENDING);
        assertThat(lastError(id)).isEqualTo("Provider unreachable or timed out");
    }

    private void processAndAwait() {
        processingService.processPendingActivations();
        await().until(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM activation WHERE status = 'PROCESSING'", Long.class) == 0);
    }

    private UUID createActivation() {
        return activationService.create(CLIENT, "EXT-" + UUID.randomUUID(), "CUST-1", "INTERNET-500").id();
    }

    private String lastError(UUID id) {
        return attemptRepository.findByActivationIdOrderByAttemptNumber(id).getLast().getError();
    }

    private void providerResponds(int status, Duration delay) {
        provider.createContext("/provider/v1/activate", exchange -> {
            exchange.getRequestBody().readAllBytes();
            sleep(delay);
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
