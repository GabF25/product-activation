package com.deroyqueit.productactivation.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("provider")
public record ProviderProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
