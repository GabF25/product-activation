package com.deroyqueit.productactivation.controller;

import com.deroyqueit.productactivation.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class OpenApiDocsTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsTheRoutesWithoutToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value("http://localhost:28080"))
                .andExpect(jsonPath("$.paths['/api/v1/requests'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/requests/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/requests'].get.responses['401']").exists())
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt']").exists());
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
