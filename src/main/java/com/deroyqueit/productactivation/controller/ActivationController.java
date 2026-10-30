package com.deroyqueit.productactivation.controller;

import com.deroyqueit.productactivation.domain.model.ActivationStatus;
import com.deroyqueit.productactivation.dto.ActivationRequest;
import com.deroyqueit.productactivation.dto.ActivationResponse;
import com.deroyqueit.productactivation.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.UUID;

@RequestMapping("/api/v1/requests")
@Tag(name = "Activations", description = "Product activation requests")
public interface ActivationController {

    String PROBLEM_JSON = "application/problem+json";

    @PostMapping
    @Operation(summary = "Create an activation",
            description = "Idempotent by externalId: the same externalId with the same content returns the "
                    + "activation already created. It starts PENDING and is processed asynchronously.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Activation created, or the existing one for this externalId"),
            @ApiResponse(responseCode = "400", description = "Invalid data",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "externalId already used with different content",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "413", description = "Body larger than 1 KB",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "415", description = "Content-Type is not application/json",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<ActivationResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
                    value = "{\"externalId\":\"EXT-12345\",\"customerId\":\"CUST-10001\",\"productCode\":\"INTERNET-500\"}")))
            @Valid @RequestBody ActivationRequest request,
            Principal client);

    @GetMapping("/{id}")
    @Operation(summary = "Find an activation",
            description = "Activations created by another client are reported as not found.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Activation found"),
            @ApiResponse(responseCode = "404", description = "Activation not found",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<ActivationResponse> findById(
            @Parameter(description = "Activation id") @PathVariable UUID id,
            Principal client);

    @GetMapping
    @Operation(summary = "List activations", description = "The client's own activations, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of activations"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or paging",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<PageResponse<ActivationResponse>> list(
            @Parameter(description = "Filter by status") @RequestParam(required = false) ActivationStatus status,
            @Parameter(description = "Filter by customer") @RequestParam(required = false) String customerId,
            @Parameter(description = "Page number, starting at 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, up to 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Principal client);
}
