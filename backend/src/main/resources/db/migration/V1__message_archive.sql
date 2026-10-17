CREATE TABLE payment_message (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mq_message_id TEXT NOT NULL,
    correlation_id TEXT,
    source_application VARCHAR(128) NOT NULL,
    source_queue TEXT NOT NULL,
    sent_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    body_type VARCHAR(16) NOT NULL CHECK (body_type IN ('TEXT','BYTES','UNSUPPORTED')),
    payload BYTEA,
    payload_size BIGINT NOT NULL CHECK (payload_size >= 0),
    redelivered BOOLEAN NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('STORED','QUARANTINED')),
    rejection_reason TEXT,
    CONSTRAINT uq_message_origin UNIQUE (source_queue, mq_message_id),
    CONSTRAINT ck_stored_payload CHECK (status <> 'STORED' OR payload IS NOT NULL)
);
CREATE INDEX idx_message_received ON payment_message (received_at DESC);
CREATE INDEX idx_message_status_id ON payment_message (status, id DESC);
CREATE INDEX idx_message_source_id ON payment_message (source_application, id DESC);
CREATE INDEX idx_message_correlation_id ON payment_message (correlation_id, id DESC);
CREATE INDEX idx_message_mq_id ON payment_message (mq_message_id, id DESC);
