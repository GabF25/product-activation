package com.deroyqueit.productactivation.filter;

import com.deroyqueit.productactivation.config.properties.ApiLimitsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RequestBodySizeFilterTest {

    private static final int MAX_BYTES = 16;

    private final RequestBodySizeFilter filter = new RequestBodySizeFilter(
            new ApiLimitsProperties(DataSize.ofBytes(MAX_BYTES), 100),
            new ProblemResponseWriter(new ObjectMapper()));

    @Test
    void rejectsChunkedBodyWithoutContentLength() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(chunkedRequest("{\"a\":1}"), response, chain);

        assertThat(response.getStatus()).isEqualTo(411);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void passesBodyWithinTheLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/requests");
        request.setContent("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static MockHttpServletRequest chunkedRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/requests") {

            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.addHeader("Transfer-Encoding", "chunked");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
