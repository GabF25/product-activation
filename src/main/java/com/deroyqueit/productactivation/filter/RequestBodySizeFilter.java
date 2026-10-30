package com.deroyqueit.productactivation.filter;

import com.deroyqueit.productactivation.config.properties.ApiLimitsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestBodySizeFilter extends OncePerRequestFilter {

    private final ApiLimitsProperties limits;
    private final ProblemResponseWriter problemWriter;

    public RequestBodySizeFilter(ApiLimitsProperties limits, ProblemResponseWriter problemWriter) {
        this.limits = limits;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isChunked(request)) {
            problemWriter.write(request, response, HttpStatus.LENGTH_REQUIRED, "length-required",
                    "Content-Length required", "The request body must be sent with a Content-Length header.");
            return;
        }
        long maxBytes = limits.maxBodySize().toBytes();
        if (request.getContentLengthLong() > maxBytes) {
            problemWriter.write(request, response, HttpStatus.PAYLOAD_TOO_LARGE, "body-too-large",
                    "Request body too large", "The request body must have at most " + maxBytes + " bytes.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isChunked(HttpServletRequest request) {
        return request.getContentLengthLong() < 0 && request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null;
    }
}
