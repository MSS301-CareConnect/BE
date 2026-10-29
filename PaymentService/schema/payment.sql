-- DDL khởi tạo DB mới của PaymentService (áp tay một lần, không Flyway — AGENTS §5).
-- DB đã có dữ liệu: chạy các file trong schema/changes/ theo thứ tự ngày.
BEGIN;

-- Chính sách nền tảng có hiệu lực theo thời gian (PAY-02 tối thiểu, D15): chưa có API ghi, seed GLOBAL ở cuối file.
CREATE TABLE platform_policies (
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

CREATE UNIQUE INDEX uk_platform_policies_scope_center_from
    ON platform_policies (scope, COALESCE(center_id, '00000000-0000-0000-0000-000000000000'::uuid), effective_from);

CREATE TABLE payments (
    payment_id          uuid          PRIMARY KEY,
    booking_id          uuid          NOT NULL,
    customer_id         uuid          NOT NULL,
    center_id           uuid          NOT NULL,
    payment_type        varchar(20)   NOT NULL DEFAULT 'DEPOSIT' CHECK (payment_type IN ('DEPOSIT','REMAINING')),
    invoice_id          uuid,
    amount              numeric(14,2) NOT NULL CHECK (amount > 0),
    refunded_amount     numeric(14,2) NOT NULL DEFAULT 0,
    currency            varchar(3)    NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    status              varchar(20)   NOT NULL CHECK (status IN ('PENDING','SUCCESS','FAILED','CANCELLED','EXPIRED')),
    paid_at             timestamptz,
    booking_notified_at timestamptz,
    version             bigint        NOT NULL DEFAULT 0,
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    CONSTRAINT uk_payments_booking_type UNIQUE (booking_id, payment_type),
    CONSTRAINT ck_payments_refunded CHECK (refunded_amount >= 0 AND refunded_amount <= amount)
);

CREATE INDEX idx_payments_customer_created ON payments(customer_id, created_at DESC);
CREATE INDEX idx_payments_center_paid ON payments(center_id, paid_at DESC) WHERE status = 'SUCCESS';

CREATE TABLE payment_attempts (
    attempt_id              uuid          PRIMARY KEY,
    payment_id              uuid          NOT NULL REFERENCES payments(payment_id) ON DELETE CASCADE,
    provider                varchar(20)   NOT NULL CHECK (provider IN ('VNPAY','STRIPE')),
    status                  varchar(20)   NOT NULL CHECK (status IN ('PENDING','SUCCESS','FAILED','CANCELLED','EXPIRED')),
    amount                  numeric(14,2) NOT NULL CHECK (amount > 0),
    currency                varchar(3)    NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    reference               varchar(100)  NOT NULL UNIQUE,
    provider_checkout_id    varchar(255)  UNIQUE,
    provider_transaction_id varchar(255),
    checkout_url            varchar(2000),
    response_code           varchar(50),
    expires_at              timestamptz   NOT NULL,
    completed_at            timestamptz,
    version                 bigint        NOT NULL DEFAULT 0,
    created_at              timestamptz   NOT NULL,
    updated_at              timestamptz   NOT NULL
);

CREATE INDEX idx_payment_attempts_payment_created ON payment_attempts(payment_id, created_at DESC);
CREATE INDEX idx_payment_attempts_status_expires ON payment_attempts(status, expires_at);

-- Tài chính của một booking: snapshot chính sách lúc tạo phiên cọc (PAY-03), tổng tiền, cọc.
CREATE TABLE booking_financials (
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

CREATE INDEX idx_booking_financials_customer ON booking_financials(customer_id);

-- Phân bổ cọc / đã thu / đã hoàn theo từng buổi (PAY-04). refunded gồm cả hoàn đang chờ.
CREATE TABLE occurrence_financials (
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

CREATE INDEX idx_occurrence_financials_booking ON occurrence_financials(booking_id);

CREATE TABLE remaining_invoices (
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

CREATE INDEX idx_remaining_invoices_customer_status ON remaining_invoices(customer_id, status);
CREATE INDEX idx_remaining_invoices_status_due ON remaining_invoices(status, due_at);

CREATE TABLE refunds (
    refund_id              uuid          PRIMARY KEY,
    booking_id             uuid          NOT NULL,
    occurrence_id          uuid,
    payment_id             uuid          NOT NULL REFERENCES payments(payment_id),
    -- Khác NULL: hoàn tiền của MỘT phiên trả trễ / trả trùng — tiền nằm ngoài payments.amount, hoàn qua đúng giao dịch đó.
    attempt_id             uuid          REFERENCES payment_attempts(attempt_id),
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

CREATE UNIQUE INDEX uk_refunds_key_occurrence_payment
    ON refunds (idempotency_key, COALESCE(occurrence_id, '00000000-0000-0000-0000-000000000000'::uuid), payment_id);
CREATE INDEX idx_refunds_status_next ON refunds(status, next_attempt_at);
CREATE INDEX idx_refunds_booking ON refunds(booking_id);
CREATE INDEX idx_refunds_customer ON refunds(customer_id, created_at DESC);
CREATE INDEX idx_refunds_attempt ON refunds(attempt_id) WHERE attempt_id IS NOT NULL;

-- Nhật ký append-only: mọi biến động tiền một dòng; không UPDATE / DELETE; detail không chứa secret / vnp_SecureHash.
CREATE TABLE payment_transactions (
    transaction_id  uuid          PRIMARY KEY,
    booking_id      uuid          NOT NULL,
    payment_id      uuid,
    refund_id       uuid,
    invoice_id      uuid,
    event_type      varchar(30)   NOT NULL CONSTRAINT ck_payment_transactions_event_type CHECK (event_type IN (
                        'CHECKOUT_CREATED','IPN','RETURN','QUERYDR','PAYMENT_SUCCEEDED','PAYMENT_FAILED',
                        'PAYMENT_EXPIRED','BOOKING_CONFIRMED','BOOKING_REJECTED','REFUND_REQUESTED','REFUND_CALL',
                        'REFUND_SUCCEEDED','REFUND_FAILED','REFUND_RETRIED','INVOICE_CREATED','INVOICE_PAID',
                        'INVOICE_WAIVED','INVOICE_REDUCED','INVOICE_OVERDUE')),
    amount          numeric(14,2),
    idempotency_key varchar(150),
    detail          varchar(1000),
    signature_valid boolean,
    created_at      timestamptz   NOT NULL
);

CREATE INDEX idx_payment_transactions_booking ON payment_transactions(booking_id, created_at);
CREATE INDEX idx_payment_transactions_key ON payment_transactions(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Outbox xác nhận cọc sang BookingService (Đợt 1b): ghi cùng transaction IPN, gửi sau commit, retry có backoff.
CREATE TABLE payment_outbox (
    outbox_id         uuid          PRIMARY KEY,
    event_type        varchar(30)   NOT NULL CHECK (event_type IN ('DEPOSIT_CONFIRMATION')),
    booking_id        uuid          NOT NULL,
    payment_id        uuid          NOT NULL REFERENCES payments(payment_id),
    payment_reference varchar(100)  NOT NULL,
    status            varchar(20)   NOT NULL CHECK (status IN ('PENDING','SENT','FAILED')),
    attempts          integer       NOT NULL DEFAULT 0,
    next_attempt_at   timestamptz   NOT NULL,
    last_error        varchar(500),
    sent_at           timestamptz,
    version           bigint        NOT NULL DEFAULT 0,
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    CONSTRAINT uk_payment_outbox_event_payment UNIQUE (event_type, payment_id)
);

CREATE INDEX idx_payment_outbox_status_next ON payment_outbox(status, next_attempt_at);

-- Seed idempotent chính sách GLOBAL (D17: cọc 30 %; bảng phí hủy GAP_ANALYSIS §1.2).
INSERT INTO platform_policies (policy_id, scope, center_id, effective_from, deposit_percent, commission_percent,
                               payment_session_ttl_minutes, deposit_due_hours, cooling_off_minutes,
                               cooling_off_min_lead_hours, refund_tiers, customer_no_show_charge_percent,
                               remaining_due_hours, note, version, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'GLOBAL', NULL, '2026-01-01T00:00:00Z', 30, 10, 15, 24, 60, 24,
        '[{"minLeadHours":0,"maxLeadHours":24,"feePercent":30},{"minLeadHours":24,"maxLeadHours":72,"feePercent":10},{"minLeadHours":72,"maxLeadHours":null,"feePercent":0}]',
        100, 48, 'Chính sách mặc định toàn nền tảng', 0, now(), now())
ON CONFLICT (policy_id) DO NOTHING;

COMMIT;
