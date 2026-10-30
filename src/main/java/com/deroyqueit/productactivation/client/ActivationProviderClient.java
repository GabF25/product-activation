package com.deroyqueit.productactivation.client;

import com.deroyqueit.productactivation.client.ActivationResult.PermanentFailure;
import com.deroyqueit.productactivation.client.ActivationResult.RetryableFailure;
import com.deroyqueit.productactivation.client.ActivationResult.Success;
import com.deroyqueit.productactivation.config.properties.ProviderProperties;
import com.deroyqueit.productactivation.domain.model.Activation;
import com.deroyqueit.productactivation.filter.CorrelationIdFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class ActivationProviderClient {

    static final String ACTIVATE_PATH = "/provider/v1/activate";
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;

    public ActivationProviderClient(ProviderProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public ActivationResult activate(Activation activation) {
        try {
            return restClient.post()
                    .uri(ACTIVATE_PATH)
                    .header(IDEMPOTENCY_KEY_HEADER, activation.id().toString())
                    .header(CorrelationIdFilter.HEADER, activation.id().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ProviderActivationPayload.from(activation))
                    .exchange((httpRequest, response) -> toResult(response));
        } catch (ResourceAccessException e) {
            return new RetryableFailure("Provider unreachable or timed out", null);
        }
    }

    private static ActivationResult toResult(ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        String reason = "Provider responded HTTP " + status.value();

        if (status.is2xxSuccessful()) {
            return new Success();
        }
        if (isThrottling(status)) {
            return new RetryableFailure(reason, retryAfter(response));
        }
        if (status.is5xxServerError() || status.isSameCodeAs(HttpStatus.REQUEST_TIMEOUT)) {
            return new RetryableFailure(reason, null);
        }
        return new PermanentFailure(reason);
    }

    private static boolean isThrottling(HttpStatusCode status) {
        return status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS) || status.isSameCodeAs(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static Duration retryAfter(ClientHttpResponse response) {
        String value = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return untilDate(value);
        }
    }

    private static Duration untilDate(String value) {
        try {
            Instant date = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration delay = Duration.between(Instant.now(), date);
            return delay.isNegative() ? Duration.ZERO : delay;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
