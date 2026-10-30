package com.deroyqueit.productactivation.filter;

import com.deroyqueit.productactivation.config.properties.ApiLimitsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static final int REQUESTS_PER_MINUTE = 2;

    private final RateLimitFilter filter = new RateLimitFilter(
            new ApiLimitsProperties(DataSize.ofKilobytes(1), REQUESTS_PER_MINUTE),
            new ProblemResponseWriter(new ObjectMapper()));

    @Test
    void rejectsRequestsAboveTheLimitWithRetryAfter() throws Exception {
        assertThat(callAs("client-1").getStatus()).isEqualTo(200);
        assertThat(callAs("client-1").getStatus()).isEqualTo(200);

        MockHttpServletResponse rejected = callAs("client-1");

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isNotBlank();
        assertThat(rejected.getContentType()).isEqualTo("application/problem+json");
    }

    @Test
    void eachClientHasItsOwnLimit() throws Exception {
        for (int i = 0; i < REQUESTS_PER_MINUTE; i++) {
            callAs("client-1");
        }

        assertThat(callAs("client-2").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse callAs(String client) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/requests");
        request.setUserPrincipal(() -> client);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
