BEGIN;

CREATE TABLE payments (
    payment_id uuid PRIMARY KEY,
    booking_id uuid NOT NULL UNIQUE,
    customer_id uuid NOT NULL,
    center_id uuid NOT NULL,
    amount numeric(14,2) NOT NULL CHECK (amount > 0),
    currency varchar(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    status varchar(20) NOT NULL CHECK (status IN ('PENDING','SUCCESS','FAILED','CANCELLED','EXPIRED')),
    paid_at timestamptz,
    booking_notified_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX idx_payments_customer_created ON payments(customer_id, created_at DESC);
CREATE INDEX idx_payments_center_paid ON payments(center_id, paid_at DESC) WHERE status = 'SUCCESS';

CREATE TABLE payment_attempts (
    attempt_id uuid PRIMARY KEY,
    payment_id uuid NOT NULL REFERENCES payments(payment_id) ON DELETE CASCADE,
    provider varchar(20) NOT NULL CHECK (provider IN ('VNPAY','STRIPE')),
    status varchar(20) NOT NULL CHECK (status IN ('PENDING','SUCCESS','FAILED','CANCELLED','EXPIRED')),
    amount numeric(14,2) NOT NULL CHECK (amount > 0),
    currency varchar(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    reference varchar(100) NOT NULL UNIQUE,
    provider_checkout_id varchar(255) UNIQUE,
    provider_transaction_id varchar(255),
    checkout_url varchar(2000),
    response_code varchar(50),
    expires_at timestamptz NOT NULL,
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX idx_payment_attempts_payment_created ON payment_attempts(payment_id, created_at DESC);

COMMIT;
