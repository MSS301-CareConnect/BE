-- Đợt 1b (2026-10-30): outbox xác nhận cọc, hoàn trả trễ / trả trùng theo từng phiên, nhật ký querydr / Booking.
-- Chỉ additive, chạy lại được. Chạy SAU 2026-10-29-payment-refunds.sql.
BEGIN;

ALTER TABLE refunds ADD COLUMN IF NOT EXISTS attempt_id uuid REFERENCES payment_attempts(attempt_id);
CREATE INDEX IF NOT EXISTS idx_refunds_attempt ON refunds(attempt_id) WHERE attempt_id IS NOT NULL;

-- CHECK inline ở bản 2026-10-29 mang tên mặc định của PostgreSQL.
ALTER TABLE payment_transactions DROP CONSTRAINT IF EXISTS payment_transactions_event_type_check;
ALTER TABLE payment_transactions DROP CONSTRAINT IF EXISTS ck_payment_transactions_event_type;
ALTER TABLE payment_transactions ADD CONSTRAINT ck_payment_transactions_event_type CHECK (event_type IN (
    'CHECKOUT_CREATED','IPN','RETURN','QUERYDR','PAYMENT_SUCCEEDED','PAYMENT_FAILED','PAYMENT_EXPIRED',
    'BOOKING_CONFIRMED','BOOKING_REJECTED','REFUND_REQUESTED','REFUND_CALL','REFUND_SUCCEEDED','REFUND_FAILED',
    'REFUND_RETRIED','INVOICE_CREATED','INVOICE_PAID','INVOICE_WAIVED','INVOICE_REDUCED','INVOICE_OVERDUE'));

CREATE TABLE IF NOT EXISTS payment_outbox (
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

CREATE INDEX IF NOT EXISTS idx_payment_outbox_status_next ON payment_outbox(status, next_attempt_at);

-- Payment SUCCESS cũ đã báo Booking (booking_notified_at) không cần outbox; chưa báo → xếp outbox để gửi lại.
INSERT INTO payment_outbox (outbox_id, event_type, booking_id, payment_id, payment_reference, status, attempts,
                            next_attempt_at, version, created_at, updated_at)
SELECT gen_random_uuid(), 'DEPOSIT_CONFIRMATION', p.booking_id, p.payment_id,
       COALESCE((SELECT a.reference FROM payment_attempts a WHERE a.payment_id = p.payment_id AND a.status = 'SUCCESS'
                 ORDER BY a.completed_at LIMIT 1), p.payment_id::text),
       'PENDING', 0, now(), 0, now(), now()
FROM payments p
WHERE p.status = 'SUCCESS' AND p.payment_type = 'DEPOSIT' AND p.booking_notified_at IS NULL
ON CONFLICT (event_type, payment_id) DO NOTHING;

COMMIT;
