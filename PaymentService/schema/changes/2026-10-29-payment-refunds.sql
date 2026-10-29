-- Đợt 1a (2026-10-29): bảng tài chính, hoàn tiền, hóa đơn REMAINING, nhật ký giao dịch, chính sách GLOBAL.
-- Chỉ additive: chạy được trên DB đang có dữ liệu (IF NOT EXISTS, backfill), chạy lại nhiều lần không lỗi.
-- psql -v ON_ERROR_STOP=1 -f schema/changes/2026-10-29-payment-refunds.sql
BEGIN;

-- payments: loại thanh toán (dữ liệu cũ đều là cọc), tiền đã hoàn, hóa đơn REMAINING.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payment_type varchar(20) NOT NULL DEFAULT 'DEPOSIT';
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refunded_amount numeric(14,2) NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS invoice_id uuid;
UPDATE payments SET payment_type = 'DEPOSIT' WHERE payment_type IS NULL;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payments_type;
ALTER TABLE payments ADD CONSTRAINT ck_payments_type CHECK (payment_type IN ('DEPOSIT','REMAINING'));
ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payments_refunded;
ALTER TABLE payments ADD CONSTRAINT ck_payments_refunded CHECK (refunded_amount >= 0 AND refunded_amount <= amount);
-- Unique cũ (inline, tên mặc định) chỉ cho một payment / booking → nới thành một payment / (booking, loại).
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_booking_id_key;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS uk_payments_booking_type;
ALTER TABLE payments ADD CONSTRAINT uk_payments_booking_type UNIQUE (booking_id, payment_type);

CREATE INDEX IF NOT EXISTS idx_payment_attempts_status_expires ON payment_attempts(status, expires_at);

CREATE TABLE IF NOT EXISTS platform_policies (
    policy_id                       uuid          PRIMARY KEY,
    scope                           varchar(20)   NOT NULL CHECK (scope IN ('GLOBAL','CENTER')),
    center_id                       uuid,
    effective_from                  timestamptz   NOT NULL,
    deposit_percent                 numeric(5,2)  NOT NULL CHECK (deposit_percent BETWEEN 0 AND 100),
    commission_percent              numeric(5,2)  NOT NULL CHECK (commission_percent BETWEEN 0 AND 100),
    payment_session_ttl_minutes     integer       NOT NULL CHECK (payment_session_ttl_minutes > 0),
    deposit_due_hours               integer       NOT NULL CHECK (deposit_due_hours > 0),
    cooling_off_minutes             integer       NOT NULL CHECK (cooling_off_minutes >= 0),
    cooling_off_min_lead_hours      integer       NOT NULL CHECK (cooling_off_min_lead_hours >= 0),
    refund_tiers                    jsonb         NOT NULL,
    customer_no_show_charge_percent numeric(5,2)  NOT NULL CHECK (customer_no_show_charge_percent BETWEEN 0 AND 100),
    remaining_due_hours             integer       NOT NULL CHECK (remaining_due_hours > 0),
    note                            varchar(500),
    version                         bigint        NOT NULL DEFAULT 0,
    created_at                      timestamptz   NOT NULL,
    updated_at                      timestamptz   NOT NULL,
    CONSTRAINT ck_platform_policies_scope_center CHECK ((scope = 'GLOBAL') = (center_id IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_platform_policies_scope_center_from
    ON platform_policies (scope, COALESCE(center_id, '00000000-0000-0000-0000-000000000000'::uuid), effective_from);

CREATE TABLE IF NOT EXISTS booking_financials (
    booking_id         uuid          PRIMARY KEY,
    customer_id        uuid          NOT NULL,
    center_id          uuid          NOT NULL,
    policy_id          uuid          NOT NULL,
    policy_snapshot    jsonb         NOT NULL,
    total_amount       numeric(14,2) NOT NULL CHECK (total_amount >= 0),
    deposit_amount     numeric(14,2) NOT NULL CHECK (deposit_amount >= 0),
    deposit_payment_id uuid,
    deposit_paid_at    timestamptz,
    status             varchar(20)   NOT NULL CHECK (status IN ('AWAITING_DEPOSIT','DEPOSIT_PAID','CLOSED')),
    version            bigint        NOT NULL DEFAULT 0,
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_booking_financials_customer ON booking_financials(customer_id);

CREATE TABLE IF NOT EXISTS occurrence_financials (
    occurrence_id   uuid          PRIMARY KEY,
    booking_id      uuid          NOT NULL REFERENCES booking_financials(booking_id),
    center_id       uuid          NOT NULL,
    start_at        timestamptz   NOT NULL,
    price_amount    numeric(14,2) NOT NULL CHECK (price_amount >= 0),
    deposit_amount  numeric(14,2) NOT NULL CHECK (deposit_amount >= 0),
    charged_amount  numeric(14,2) NOT NULL DEFAULT 0 CHECK (charged_amount >= 0),
    refunded_amount numeric(14,2) NOT NULL DEFAULT 0 CHECK (refunded_amount >= 0),
    final_status    varchar(20)   CHECK (final_status IN ('COMPLETED','CANCELLED','MISSED','CUSTOMER_NO_SHOW')),
    version         bigint        NOT NULL DEFAULT 0,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    CONSTRAINT ck_occurrence_financials_refund CHECK (refunded_amount <= deposit_amount + charged_amount)
);

CREATE INDEX IF NOT EXISTS idx_occurrence_financials_booking ON occurrence_financials(booking_id);

CREATE TABLE IF NOT EXISTS remaining_invoices (
    invoice_id   uuid          PRIMARY KEY,
    booking_id   uuid          NOT NULL UNIQUE,
    customer_id  uuid          NOT NULL,
    center_id    uuid          NOT NULL,
    amount       numeric(14,2) NOT NULL CHECK (amount >= 0),
    due_at       timestamptz   NOT NULL,
    status       varchar(20)   NOT NULL CHECK (status IN ('PENDING','PAID','OVERDUE','WAIVED')),
    payment_id   uuid,
    paid_at      timestamptz,
    waived_by    uuid,
    waive_reason varchar(500),
    version      bigint        NOT NULL DEFAULT 0,
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_remaining_invoices_customer_status ON remaining_invoices(customer_id, status);
CREATE INDEX IF NOT EXISTS idx_remaining_invoices_status_due ON remaining_invoices(status, due_at);

CREATE TABLE IF NOT EXISTS refunds (
    refund_id              uuid          PRIMARY KEY,
    booking_id             uuid          NOT NULL,
    occurrence_id          uuid,
    payment_id             uuid          NOT NULL REFERENCES payments(payment_id),
    customer_id            uuid          NOT NULL,
    center_id              uuid          NOT NULL,
    complaint_id           uuid,
    amount                 numeric(14,2) NOT NULL CHECK (amount > 0),
    fee_amount             numeric(14,2) NOT NULL DEFAULT 0 CHECK (fee_amount >= 0),
    reason                 varchar(30)   NOT NULL CHECK (reason IN ('CUSTOMER_CANCEL','CENTER_REJECT','CENTER_CANCEL',
                               'CENTER_LOCKED','CAREGIVER_MISSED','EXPIRED','COMPLAINT_ACCEPTED','LATE_PAYMENT',
                               'BOOKING_NOT_CONFIRMABLE','DUPLICATE')),
    cancelled_by           varchar(20)   CHECK (cancelled_by IN ('CUSTOMER','CENTER','SYSTEM','ADMIN')),
    requested_by           uuid,
    status                 varchar(20)   NOT NULL CHECK (status IN ('REFUND_PENDING','REFUNDED','FAILED')),
    idempotency_key        varchar(150)  NOT NULL,
    provider_request_id    varchar(100),
    provider_response_code varchar(20),
    attempts               integer       NOT NULL DEFAULT 0,
    next_attempt_at        timestamptz   NOT NULL,
    last_error             varchar(500),
    refunded_at            timestamptz,
    version                bigint        NOT NULL DEFAULT 0,
    created_at             timestamptz   NOT NULL,
    updated_at             timestamptz   NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_refunds_key_occurrence_payment
    ON refunds (idempotency_key, COALESCE(occurrence_id, '00000000-0000-0000-0000-000000000000'::uuid), payment_id);
CREATE INDEX IF NOT EXISTS idx_refunds_status_next ON refunds(status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_refunds_booking ON refunds(booking_id);
CREATE INDEX IF NOT EXISTS idx_refunds_customer ON refunds(customer_id, created_at DESC);

CREATE TABLE IF NOT EXISTS payment_transactions (
    transaction_id  uuid          PRIMARY KEY,
    booking_id      uuid          NOT NULL,
    payment_id      uuid,
    refund_id       uuid,
    invoice_id      uuid,
    event_type      varchar(30)   NOT NULL CHECK (event_type IN ('CHECKOUT_CREATED','IPN','RETURN','PAYMENT_SUCCEEDED',
                        'PAYMENT_FAILED','PAYMENT_EXPIRED','REFUND_REQUESTED','REFUND_CALL','REFUND_SUCCEEDED',
                        'REFUND_FAILED','REFUND_RETRIED','INVOICE_CREATED','INVOICE_PAID','INVOICE_WAIVED',
                        'INVOICE_REDUCED','INVOICE_OVERDUE')),
    amount          numeric(14,2),
    idempotency_key varchar(150),
    detail          varchar(1000),
    signature_valid boolean,
    created_at      timestamptz   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_booking ON payment_transactions(booking_id, created_at);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_key ON payment_transactions(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

INSERT INTO platform_policies (policy_id, scope, center_id, effective_from, deposit_percent, commission_percent,
                               payment_session_ttl_minutes, deposit_due_hours, cooling_off_minutes,
                               cooling_off_min_lead_hours, refund_tiers, customer_no_show_charge_percent,
                               remaining_due_hours, note, version, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'GLOBAL', NULL, '2026-01-01T00:00:00Z', 30, 10, 15, 24, 60, 24,
        '[{"minLeadHours":0,"maxLeadHours":24,"feePercent":30},{"minLeadHours":24,"maxLeadHours":72,"feePercent":10},{"minLeadHours":72,"maxLeadHours":null,"feePercent":0}]',
        100, 48, 'Chính sách mặc định toàn nền tảng', 0, now(), now())
ON CONFLICT (policy_id) DO NOTHING;

COMMIT;
