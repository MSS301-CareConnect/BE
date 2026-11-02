-- Đặc tả booking v1.0 đợt 4 (2026-11-02, BE/docs/2026-11-02-BOOKING_FLOW_REVIEW.md §9): thanh toán đầu theo % của Booking,
-- phần còn lại trước buổi đầu, quá hạn → giữ cọc, hoàn 100 / 70 theo center booking policy. Chạy lại được.
-- Chạy SAU 2026-10-30-payment-safety.sql.
BEGIN;

-- Hạn trả phần còn lại trước buổi đầu (NULL = SHIFT 100 % / booking cũ thu sau hoàn tất — giữ nguyên dữ liệu cũ).
ALTER TABLE booking_financials ADD COLUMN IF NOT EXISTS remaining_due_at timestamptz;

-- CHECK inline của bản trước mang tên mặc định của PostgreSQL.
ALTER TABLE remaining_invoices DROP CONSTRAINT IF EXISTS remaining_invoices_status_check;
ALTER TABLE remaining_invoices DROP CONSTRAINT IF EXISTS ck_remaining_invoices_status;
ALTER TABLE remaining_invoices ADD CONSTRAINT ck_remaining_invoices_status
    CHECK (status IN ('PENDING','PAID','OVERDUE','WAIVED','EXPIRED','CANCELLED'));

ALTER TABLE refunds DROP CONSTRAINT IF EXISTS refunds_reason_check;
ALTER TABLE refunds DROP CONSTRAINT IF EXISTS ck_refunds_reason;
ALTER TABLE refunds ADD CONSTRAINT ck_refunds_reason CHECK (reason IN ('CUSTOMER_CANCEL','CENTER_REJECT','CENTER_CANCEL',
    'CENTER_LOCKED','CAREGIVER_MISSED','EXPIRED','COMPLAINT_ACCEPTED','LATE_PAYMENT','BOOKING_NOT_CONFIRMABLE','DUPLICATE',
    'PAYMENT_OVERDUE'));

ALTER TABLE payment_outbox DROP CONSTRAINT IF EXISTS payment_outbox_event_type_check;
ALTER TABLE payment_outbox DROP CONSTRAINT IF EXISTS ck_payment_outbox_event_type;
ALTER TABLE payment_outbox ADD CONSTRAINT ck_payment_outbox_event_type
    CHECK (event_type IN ('DEPOSIT_CONFIRMATION','REMAINING_CONFIRMATION','PAYMENT_OVERDUE'));

-- Q5: hoàn tiền theo mốc của center booking policy (mặc định 24h, 100 % / 70 %), bỏ cooling-off và bậc 72h. Snapshot cũ
-- chưa có các khóa này → bổ sung mặc định của spec; % giữ cọc khi quá hạn (M3) mặc định 100.
UPDATE booking_financials
SET policy_snapshot = policy_snapshot || jsonb_build_object(
        'refundThresholdMinutes', 1440,
        'refundPercentBeforeThreshold', 100,
        'refundPercentAfterThreshold', 70,
        'overduePaymentForfeitPercent', 100)
WHERE NOT (policy_snapshot ? 'refundThresholdMinutes')
   OR policy_snapshot -> 'refundThresholdMinutes' = 'null'::jsonb;

COMMIT;
