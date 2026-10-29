-- Đợt 4 (2026-10-30, H-08 / H-11): admin bỏ qua sự kiện cascade FAILED đã xử lý tay → SKIPPED, không chặn sự kiện sau.
-- Tên CHECK do PostgreSQL tự sinh — kiểm lại bằng \d provider_outbox trước khi chạy.
BEGIN;
ALTER TABLE provider_outbox DROP CONSTRAINT IF EXISTS provider_outbox_status_check;
ALTER TABLE provider_outbox ADD CONSTRAINT provider_outbox_status_check
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'));
-- H-11: caregiver ngừng hoạt động → Booking gắn cờ đổi người cho buổi sắp tới.
ALTER TABLE provider_outbox DROP CONSTRAINT IF EXISTS provider_outbox_event_type_check;
ALTER TABLE provider_outbox ADD CONSTRAINT provider_outbox_event_type_check
    CHECK (event_type IN ('ACTIVATE', 'SUSPEND', 'RESUME', 'CAREGIVER_DEACTIVATED'));
COMMIT;
