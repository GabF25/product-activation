package com.deroyqueit.productactivation.controller;

import com.deroyqueit.productactivation.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class JwtValidationTests {

    private static final String REQUESTS_URL = "/api/v1/requests";
    private static final String AUDIENCE = "product-activation";
    private static final String OTHER_SECRET = "another-secret-with-at-least-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @Value("${security.jwt.dev-secret}")
    private String secret;

    @Test
    void acceptsValidToken() throws Exception {
        String token = sign(secret, claims(AUDIENCE, Instant.now().plus(Duration.ofHours(1))));

        mockMvc.perform(get(REQUESTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = sign(secret, claims(AUDIENCE, Instant.now().minus(Duration.ofHours(1))));

        mockMvc.perform(get(REQUESTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() throws Exception {
        String token = sign(OTHER_SECRET, claims(AUDIENCE, Instant.now().plus(Duration.ofHours(1))));

        mockMvc.perform(get(REQUESTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenForAnotherAudience() throws Exception {
        String token = sign(secret, claims("another-api", Instant.now().plus(Duration.ofHours(1))));

        mockMvc.perform(get(REQUESTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readTokenFromDevEndpointReadsButCannotCreate() throws Exception {
        String token = devToken("READ");

        mockMvc.perform(get(REQUESTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post(REQUESTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"EXT-1\",\"customerId\":\"CUST-1\",\"productCode\":\"INTERNET-500\"}"))
                .andExpect(status().isForbidden());
    }

    private String devToken(String scope) throws Exception {
        String response = mockMvc.perform(post("/dev/token").param("scope", scope))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private static JwtClaimsSet claims(String audience, Instant expiresAt) {
        return JwtClaimsSet.builder()
                .subject("client-1")
                .audience(List.of(audience))
                .claim("scope", "activation:read")
                .issuedAt(expiresAt.minus(Duration.ofHours(2)))
                .expiresAt(expiresAt)
                .build();
    }

    private static String sign(String secret, JwtClaimsSet claims) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }
}
