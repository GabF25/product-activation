package com.deroyqueit.productactivation.domain.exception;

public class ExternalIdConflictException extends RuntimeException {

    public ExternalIdConflictException(String externalId) {
        super("externalId " + externalId + " already used with different data");
    }
}
