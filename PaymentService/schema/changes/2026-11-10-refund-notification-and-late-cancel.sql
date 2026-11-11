-- 2026-11-10 — báo khách khi hoàn tiền xong (payment_outbox REFUND_COMPLETED) cho DB ĐÃ CÓ dữ liệu.
-- Áp SAU 2026-11-09-wallets.sql:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-10-refund-notification-and-late-cancel.sql
-- Thay đổi:
--   * payment_outbox.event_type thêm REFUND_COMPLETED (gửi POST /api/v1/notifications của BookingService).
--   * Khóa trùng outbox gồm cả payment_reference: một payment có nhiều lệnh hoàn ("refund=<refundId>"). Dòng cũ vẫn duy
--     nhất theo (event_type, payment_id) nên không cần dọn dữ liệu.
BEGIN;

ALTER TABLE payment_outbox DROP CONSTRAINT IF EXISTS ck_payment_outbox_event_type;
ALTER TABLE payment_outbox ADD CONSTRAINT ck_payment_outbox_event_type
    CHECK (event_type IN ('DEPOSIT_CONFIRMATION','REMAINING_CONFIRMATION','PAYMENT_OVERDUE','REFUND_COMPLETED'));

ALTER TABLE payment_outbox DROP CONSTRAINT IF EXISTS uk_payment_outbox_event_payment;
ALTER TABLE payment_outbox ADD CONSTRAINT uk_payment_outbox_event_payment
    UNIQUE (event_type, payment_id, payment_reference);

COMMIT;
