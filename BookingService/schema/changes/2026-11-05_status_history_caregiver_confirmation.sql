-- 2026-11-05 — booking_status_history thiếu PENDING_CAREGIVER_CONFIRMATION (CR-3, 2026-11-03 chỉ thêm vào ck_bookings_status):
-- mọi booking khách chọn sẵn caregiver bị 400 "Invalid request" khi ghi lịch sử trạng thái. Idempotent, áp tay.
BEGIN;

ALTER TABLE booking_status_history DROP CONSTRAINT IF EXISTS ck_booking_status_history_from;
ALTER TABLE booking_status_history ADD CONSTRAINT ck_booking_status_history_from CHECK (from_status IS NULL OR from_status IN (
    'PENDING_CENTER_CONFIRMATION', 'PENDING_CAREGIVER_CONFIRMATION', 'PENDING_CAREGIVER_ASSIGNMENT', 'AWAITING_INITIAL_PAYMENT',
    'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED'));

ALTER TABLE booking_status_history DROP CONSTRAINT IF EXISTS ck_booking_status_history_to;
ALTER TABLE booking_status_history ADD CONSTRAINT ck_booking_status_history_to CHECK (to_status IN (
    'PENDING_CENTER_CONFIRMATION', 'PENDING_CAREGIVER_CONFIRMATION', 'PENDING_CAREGIVER_ASSIGNMENT', 'AWAITING_INITIAL_PAYMENT',
    'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED'));

COMMIT;
