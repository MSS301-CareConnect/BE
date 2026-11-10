-- 2026-11-10 — mốc "hủy sát giờ không hoàn" theo từng trung tâm (trước đó là hằng 2 giờ trong PaymentService) cho DB ĐÃ CÓ
-- dữ liệu. Áp SAU 2026-11-10_caregiver_absences.sql:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-10_late_cancel_no_refund.sql
-- Policy đã lưu nhận 120 phút như trước; trung tâm đặt mốc hoàn ngắn hơn 2 giờ thì lấy đúng mốc hoàn (không vượt).
BEGIN;

ALTER TABLE center_booking_policies ADD COLUMN IF NOT EXISTS late_cancel_no_refund_minutes integer NOT NULL DEFAULT 120;
UPDATE center_booking_policies
SET late_cancel_no_refund_minutes = LEAST(late_cancel_no_refund_minutes, refund_threshold_minutes)
WHERE late_cancel_no_refund_minutes > refund_threshold_minutes;

ALTER TABLE center_booking_policies DROP CONSTRAINT IF EXISTS ck_center_booking_policies_late_cancel;
ALTER TABLE center_booking_policies ADD CONSTRAINT ck_center_booking_policies_late_cancel CHECK (
    late_cancel_no_refund_minutes BETWEEN 0 AND refund_threshold_minutes);

COMMIT;
