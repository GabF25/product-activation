package com.deroyqueit.productactivation.client;

import com.deroyqueit.productactivation.client.ActivationResult.PermanentFailure;
import com.deroyqueit.productactivation.client.ActivationResult.RetryableFailure;
import com.deroyqueit.productactivation.client.ActivationResult.Success;
import com.deroyqueit.productactivation.config.properties.ProviderProperties;
import com.deroyqueit.productactivation.domain.model.Activation;
import com.deroyqueit.productactivation.filter.CorrelationIdFilter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActivationProviderClientTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);

    private final Activation activation = Activation.create("client-1", "EXT-1", "CUST-1", "INTERNET-500");

    private HttpServer provider;
    private ActivationProviderClient client;
    private volatile String receivedIdempotencyKey;
    private volatile String receivedCorrelationId;
    private volatile String receivedBody;

    @BeforeEach
    void startProvider() throws IOException {
        provider = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        provider.start();
        String baseUrl = "http://localhost:" + provider.getAddress().getPort();
        client = new ActivationProviderClient(new ProviderProperties(baseUrl, Duration.ofSeconds(1), READ_TIMEOUT));
    }

    @AfterEach
    void stopProvider() {
        provider.stop(0);
    }

    @Test
    void successSendsIdempotencyKeyCorrelationIdAndPayload() {
        providerResponds(200);

        ActivationResult result = client.activate(activation);

        assertThat(result).isInstanceOf(Success.class);
        assertThat(receivedIdempotencyKey).isEqualTo(activation.id().toString());
        assertThat(receivedCorrelationId).isEqualTo(activation.id().toString());
        assertThat(receivedBody).contains("\"externalId\":\"EXT-1\"", "\"productCode\":\"INTERNET-500\"");
    }

    @Test
    void badRequestIsPermanentFailure() {
        providerResponds(400);

        assertThat(client.activate(activation)).isInstanceOf(PermanentFailure.class);
    }

    @Test
    void serverErrorIsRetryable() {
        providerResponds(500);

        ActivationResult result = client.activate(activation);

        assertThat(result).isInstanceOf(RetryableFailure.class);
        assertThat(((RetryableFailure) result).retryAfter()).isNull();
    }

    @Test
    void tooManyRequestsHonorsRetryAfter() {
        providerResponds(429, Map.of("Retry-After", "7"), Duration.ZERO);

        ActivationResult result = client.activate(activation);

        assertThat(result).isEqualTo(new RetryableFailure("Provider responded HTTP 429", Duration.ofSeconds(7)));
    }

    @Test
    void serviceUnavailableHonorsRetryAfterAsHttpDate() {
        String inTwoMinutes = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(2));
        providerResponds(503, Map.of("Retry-After", inTwoMinutes), Duration.ZERO);

        ActivationResult result = client.activate(activation);

        assertThat(result).isInstanceOf(RetryableFailure.class);
        assertThat(((RetryableFailure) result).retryAfter()).isBetween(Duration.ofMinutes(1), Duration.ofMinutes(2));
    }

    @Test
    void responseAfterReadTimeoutIsRetryable() {
        providerResponds(200, Map.of(), READ_TIMEOUT.multipliedBy(3));

        assertThat(client.activate(activation)).isInstanceOf(RetryableFailure.class);
    }

    private void providerResponds(int status) {
        providerResponds(status, Map.of(), Duration.ZERO);
    }

    private void providerResponds(int status, Map<String, String> headers, Duration delay) {
        provider.createContext(ActivationProviderClient.ACTIVATE_PATH, exchange -> {
            receivedIdempotencyKey = exchange.getRequestHeaders().getFirst(ActivationProviderClient.IDEMPOTENCY_KEY_HEADER);
            receivedCorrelationId = exchange.getRequestHeaders().getFirst(CorrelationIdFilter.HEADER);
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            sleep(delay);
            headers.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
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
