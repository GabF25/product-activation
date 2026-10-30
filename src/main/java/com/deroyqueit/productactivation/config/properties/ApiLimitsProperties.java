package com.deroyqueit.productactivation.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("api.limits")
public record ApiLimitsProperties(DataSize maxBodySize, int requestsPerMinute) {
}
