-- 2026-11-10 — yêu cầu rút tiền của khách / caregiver chuyển về trung tâm xử lý, chuyển khoản giả lập, báo kết quả.
-- Áp SAU 2026-11-10-refund-notification-and-late-cancel.sql:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-10-withdrawals-by-center.sql
-- Thay đổi:
--   * payment_outbox: thêm WITHDRAWAL_COMPLETED / WITHDRAWAL_REJECTED (POST /api/v1/notifications của BookingService cho
--     người rút); hai loại này không gắn booking / payment nên booking_id, payment_id cho phép NULL
--     (payment_reference = "withdrawal=<withdrawalId>").
--   * wallet_withdrawals.center_id: với ví CUSTOMER / CAREGIVER là trung tâm xử lý (trung tâm đã tạo ra khoản tiền);
--     NULL = admin xử lý như cũ (yêu cầu cũ, tiền không gắn trung tâm). Không backfill — yêu cầu đang chờ giữ nguyên ở admin.
--   * Index hàng đợi trung tâm theo (center_id, status).
BEGIN;

ALTER TABLE payment_outbox DROP CONSTRAINT IF EXISTS ck_payment_outbox_event_type;
ALTER TABLE payment_outbox ADD CONSTRAINT ck_payment_outbox_event_type
    CHECK (event_type IN ('DEPOSIT_CONFIRMATION','REMAINING_CONFIRMATION','PAYMENT_OVERDUE','REFUND_COMPLETED',
                          'WITHDRAWAL_COMPLETED','WITHDRAWAL_REJECTED'));
ALTER TABLE payment_outbox ALTER COLUMN booking_id DROP NOT NULL;
ALTER TABLE payment_outbox ALTER COLUMN payment_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_wallet_withdrawals_center_status
    ON wallet_withdrawals(center_id, status, created_at DESC) WHERE center_id IS NOT NULL;

COMMIT;
