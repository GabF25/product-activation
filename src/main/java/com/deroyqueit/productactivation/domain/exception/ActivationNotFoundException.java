package com.deroyqueit.productactivation.domain.exception;

import java.util.UUID;

public class ActivationNotFoundException extends RuntimeException {

    public ActivationNotFoundException(UUID id) {
        super("Activation not found: " + id);
    }
}
