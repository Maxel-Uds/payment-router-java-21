DROP TABLE IF EXISTS payments;

CREATE UNLOGGED TABLE payments (
    correlation_id VARCHAR(150) PRIMARY KEY NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    provider VARCHAR(10) NOT NULL
);

CREATE INDEX payments_requested_at ON payments (requested_at);
