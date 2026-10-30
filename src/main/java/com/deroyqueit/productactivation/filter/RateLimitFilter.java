package com.deroyqueit.productactivation.filter;

import com.deroyqueit.productactivation.config.properties.ApiLimitsProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ApiLimitsProperties limits;
    private final ProblemResponseWriter problemWriter;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(ApiLimitsProperties limits, ProblemResponseWriter problemWriter) {
        this.limits = limits;
        this.problemWriter = problemWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Principal client = request.getUserPrincipal();
        if (client == null) {
            chain.doFilter(request, response);
            return;
        }

        ConsumptionProbe probe = buckets.computeIfAbsent(client.getName(), name -> newBucket())
                .tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        problemWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded",
                "Too many requests", "Limit of " + limits.requestsPerMinute() + " requests per minute reached.");
    }

    private Bucket newBucket() {
        int perMinute = limits.requestsPerMinute();
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }
}
