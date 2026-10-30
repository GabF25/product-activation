package com.deroyqueit.productactivation.controller;

import com.deroyqueit.productactivation.IntegrationTest;
import com.deroyqueit.productactivation.repository.ActivationRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ActivationControllerTests {

    private static final String REQUESTS_URL = "/api/v1/requests";
    private static final String CLIENT = "client-1";
    private static final String OTHER_CLIENT = "client-2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActivationRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void createsActivationWithValidData() throws Exception {
        mockMvc.perform(post(REQUESTS_URL).with(writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("EXT-1", "CUST-1", "INTERNET-500")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/requests/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.externalId").value("EXT-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void rejectsInvalidData() throws Exception {
        mockMvc.perform(post(REQUESTS_URL).with(writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("EXT-1", "", "INTERNET-500")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field", hasItem("customerId")))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void unsupportedContentTypeIsProblemDetailWithTraceId() throws Exception {
        mockMvc.perform(post(REQUESTS_URL).with(writer())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("EXT-1"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void retryWithSameContentReplaysOriginalResponse() throws Exception {
        String firstResponse = createActivationResponse(CLIENT, "EXT-1", "CUST-1", "INTERNET-500");
        String firstId = JsonPath.read(firstResponse, "$.id");

        mockMvc.perform(post(REQUESTS_URL).with(writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("EXT-1", "CUST-1", "INTERNET-500")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/requests/" + firstId)))
                .andExpect(content().string(firstResponse));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void sameExternalIdWithDifferentContentIsConflict() throws Exception {
        createActivation("EXT-1", "CUST-1", "INTERNET-500");

        mockMvc.perform(post(REQUESTS_URL).with(writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("EXT-1", "CUST-1", "TV-100")))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void findsExistingActivation() throws Exception {
        String id = createActivation("EXT-1", "CUST-1", "INTERNET-500");

        mockMvc.perform(get(REQUESTS_URL + "/" + id).with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void unknownActivationIsNotFound() throws Exception {
        mockMvc.perform(get(REQUESTS_URL + "/" + UUID.randomUUID()).with(reader()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void listsOnlyOwnActivationsWithFiltersAndPaging() throws Exception {
        createActivation("EXT-1", "CUST-1", "INTERNET-500");
        createActivation("EXT-2", "CUST-1", "TV-100");
        createActivation("EXT-3", "CUST-2", "INTERNET-500");
        createActivation(OTHER_CLIENT, "EXT-4", "CUST-1", "INTERNET-500");

        mockMvc.perform(get(REQUESTS_URL).with(reader())
                        .param("customerId", "CUST-1")
                        .param("status", "PENDING")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].externalId").value("EXT-2"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get(REQUESTS_URL).with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void listingRejectsPageSizeAboveLimit() throws Exception {
        mockMvc.perform(get(REQUESTS_URL).with(reader()).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void activationFromAnotherClientIsNotFound() throws Exception {
        String id = createActivation("EXT-1", "CUST-1", "INTERNET-500");

        mockMvc.perform(get(REQUESTS_URL + "/" + id).with(reader(OTHER_CLIENT)))
                .andExpect(status().isNotFound());
    }

    @Test
    void sameExternalIdFromAnotherClientIsANewActivation() throws Exception {
        String firstId = createActivation("EXT-1", "CUST-1", "INTERNET-500");

        mockMvc.perform(post(REQUESTS_URL).with(writer(OTHER_CLIENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("EXT-1", "CUST-1", "INTERNET-500")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(not(firstId)));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(REQUESTS_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void creationWithReadOnlyScopeIsForbidden() throws Exception {
        mockMvc.perform(post(REQUESTS_URL).with(reader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("EXT-1", "CUST-1", "INTERNET-500")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    private String createActivation(String externalId, String customerId, String productCode) throws Exception {
        return createActivation(CLIENT, externalId, customerId, productCode);
    }

    private String createActivation(String client, String externalId, String customerId, String productCode)
            throws Exception {
        return JsonPath.read(createActivationResponse(client, externalId, customerId, productCode), "$.id");
    }

    private String createActivationResponse(String client, String externalId, String customerId, String productCode)
            throws Exception {
        return mockMvc.perform(post(REQUESTS_URL).with(writer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(externalId, customerId, productCode)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static String body(String externalId, String customerId, String productCode) {
        return """
                {"externalId": "%s", "customerId": "%s", "productCode": "%s"}
                """.formatted(externalId, customerId, productCode);
    }

    private static RequestPostProcessor writer() {
        return writer(CLIENT);
    }

    private static RequestPostProcessor reader() {
        return reader(CLIENT);
    }

    private static RequestPostProcessor writer(String client) {
        return jwt().jwt(token -> token.subject(client))
                .authorities(new SimpleGrantedAuthority("SCOPE_activation:write"));
    }

    private static RequestPostProcessor reader(String client) {
        return jwt().jwt(token -> token.subject(client))
                .authorities(new SimpleGrantedAuthority("SCOPE_activation:read"));
    }
}
