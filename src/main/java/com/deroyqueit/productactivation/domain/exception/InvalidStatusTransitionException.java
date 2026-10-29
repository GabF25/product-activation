package com.deroyqueit.productactivation.domain.exception;

import com.deroyqueit.productactivation.domain.model.ActivationStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(ActivationStatus from, ActivationStatus to) {
        super("Invalid status transition: " + from + " -> " + to);
    }
}
