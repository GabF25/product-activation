CREATE TABLE activation (
    id              UUID          PRIMARY KEY,
    client_id       VARCHAR(100)  NOT NULL,
    external_id     VARCHAR(64)   NOT NULL,
    customer_id     VARCHAR(64)   NOT NULL,
    product_code    VARCHAR(64)   NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uk_activation_client_external_id UNIQUE (client_id, external_id),
    CONSTRAINT ck_activation_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_activation_pending
    ON activation (next_attempt_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_activation_processing
    ON activation (updated_at)
    WHERE status = 'PROCESSING';

CREATE INDEX idx_activation_listing
    ON activation (client_id, customer_id, status, created_at DESC);

CREATE TABLE activation_attempt (
    id             UUID          PRIMARY KEY,
    activation_id  UUID          NOT NULL,
    attempt_number INT           NOT NULL,
    attempted_at   TIMESTAMPTZ   NOT NULL,
    error          VARCHAR(255),

    CONSTRAINT fk_activation_attempt_activation
        FOREIGN KEY (activation_id) REFERENCES activation (id) ON DELETE CASCADE,
    CONSTRAINT uk_activation_attempt_number UNIQUE (activation_id, attempt_number)
);
