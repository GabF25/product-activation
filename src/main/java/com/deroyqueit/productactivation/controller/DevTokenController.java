package com.deroyqueit.productactivation.controller;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@ConditionalOnProperty("security.jwt.dev-secret")
@Tag(name = "Test token", description = "Local only: issues tokens for testing the API")
public class DevTokenController {

    private static final Duration VALIDITY = Duration.ofHours(1);
    private static final String SUBJECT = "local-client";

    private final JwtEncoder encoder;
    private final String audience;

    public DevTokenController(@Value("${security.jwt.dev-secret}") String secret,
                              @Value("${spring.security.oauth2.resourceserver.jwt.audiences}") String audience) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.audience = audience;
    }

    @PostMapping("/dev/token")
    @SecurityRequirements
    @Operation(summary = "Issue a test token with the chosen scope")
    public DevToken issue(@RequestParam TokenScope scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(SUBJECT)
                .audience(List.of(audience))
                .claim("scope", scope.value)
                .issuedAt(now)
                .expiresAt(now.plus(VALIDITY))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new DevToken(token, VALIDITY.toSeconds());
    }

    public enum TokenScope {
        READ("activation:read"),
        WRITE("activation:write"),
        READ_WRITE("activation:read activation:write");

        private final String value;

        TokenScope(String value) {
            this.value = value;
        }
    }

    public record DevToken(String accessToken, long expiresIn) {
    }
}
