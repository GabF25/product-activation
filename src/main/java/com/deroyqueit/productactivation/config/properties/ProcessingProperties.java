package com.deroyqueit.productactivation.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("activation.processing")
public record ProcessingProperties(
        int batchSize,
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        Duration stuckTimeout
) {
}
