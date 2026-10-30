package com.deroyqueit.productactivation.client;

import java.time.Duration;

public sealed interface ActivationResult {

    record Success() implements ActivationResult {
    }

    record RetryableFailure(String reason, Duration retryAfter) implements ActivationResult {
    }

    record PermanentFailure(String reason) implements ActivationResult {
    }
}
