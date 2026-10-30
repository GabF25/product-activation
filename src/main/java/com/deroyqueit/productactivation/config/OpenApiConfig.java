package com.deroyqueit.productactivation.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";
    private static final String PROBLEM_SCHEMA = "#/components/schemas/ProblemDetail";

    @Bean
    OpenAPI openApi(@Value("${server.port}") int port) {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Activation API")
                        .version("v1")
                        .description("""
                                Registers product activation requests and activates them asynchronously in the \
                                external provider. Locally, generate a token with `scripts/dev-token.sh` and use \
                                it in Authorize."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addServersItem(new Server().url("http://localhost:" + port).description("Local"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    OpenApiCustomizer commonResponses() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                operation.getResponses()
                        .addApiResponse("401", problem("Missing or invalid token"))
                        .addApiResponse("403", problem("Token without the required scope"))
                        .addApiResponse("429", problem("Too many requests, retry after the Retry-After header"))));
    }

    private static ApiResponse problem(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA))));
    }
}
