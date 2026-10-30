package com.deroyqueit.productactivation.controller;

import com.deroyqueit.productactivation.domain.model.Activation;
import com.deroyqueit.productactivation.domain.model.ActivationStatus;
import com.deroyqueit.productactivation.dto.ActivationRequest;
import com.deroyqueit.productactivation.dto.ActivationResponse;
import com.deroyqueit.productactivation.dto.PageResponse;
import com.deroyqueit.productactivation.service.ActivationService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.Principal;
import java.util.UUID;

@RestController
public class ActivationControllerImpl implements ActivationController {

    private final ActivationService service;

    public ActivationControllerImpl(ActivationService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ActivationResponse> create(ActivationRequest request, Principal client) {
        Activation created = service.create(client.getName(), request.externalId(), request.customerId(),
                request.productCode());
        return ResponseEntity.created(locationOf(created.id())).body(ActivationResponse.from(created));
    }

    @Override
    public ResponseEntity<ActivationResponse> findById(UUID id, Principal client) {
        return ResponseEntity.ok(ActivationResponse.from(service.findById(client.getName(), id)));
    }

    @Override
    public ResponseEntity<PageResponse<ActivationResponse>> list(ActivationStatus status, String customerId, int page, int size,
                                                       Principal client) {
        Page<Activation> activations = service.list(client.getName(), status, customerId, page, size);
        return ResponseEntity.ok(PageResponse.from(activations, ActivationResponse::from));
    }

    private static URI locationOf(UUID id) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }
}
