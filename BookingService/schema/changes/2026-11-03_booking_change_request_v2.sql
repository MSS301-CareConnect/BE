-- Yêu cầu thay đổi booking v2 (2026-11-03, docs/2026-11-03-BOOKING_CHANGE_REQUEST_V2.md) cho DB ĐÃ CÓ dữ liệu. DB mới
-- dùng schema/booking.sql. Áp SAU 2026-11-03_booking_spec_v1_q11_q12.sql:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-03_booking_change_request_v2.sql
-- Thay đổi (chỉ thêm, không đổi dữ liệu cũ):
--   * CR-3: trạng thái PENDING_CAREGIVER_CONFIRMATION (khách chọn caregiver → caregiver tự nhận / từ chối); scheduler quét
--     hạn trả lời của cả trung tâm lẫn caregiver; index cho danh sách đơn chờ của caregiver.
--   * CR-2: booking_events.old_value / new_value + loại SERVICE_ADDRESS_CHANGED (đổi địa chỉ chăm sóc).
--   * Thông báo mới: CAREGIVER_REQUEST, BOOKING_HANDED_OVER, BOOKING_ADDRESS_CHANGED.
BEGIN;

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_status;
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING_CENTER_CONFIRMATION',
    'PENDING_CAREGIVER_CONFIRMATION', 'PENDING_CAREGIVER_ASSIGNMENT', 'AWAITING_INITIAL_PAYMENT', 'CONFIRMED',
    'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED'));

DROP INDEX IF EXISTS idx_bookings_acceptance_due;
CREATE INDEX idx_bookings_acceptance_due ON bookings (acceptance_due_at)
    WHERE status IN ('PENDING_CENTER_CONFIRMATION', 'PENDING_CAREGIVER_CONFIRMATION');
CREATE INDEX idx_bookings_caregiver_requests ON bookings (caregiver_user_id, created_at DESC)
    WHERE status = 'PENDING_CAREGIVER_CONFIRMATION';

ALTER TABLE booking_events ADD COLUMN old_value text;
ALTER TABLE booking_events ADD COLUMN new_value text;
ALTER TABLE booking_events DROP CONSTRAINT IF EXISTS ck_booking_events_type;
ALTER TABLE booking_events ADD CONSTRAINT ck_booking_events_type CHECK (event_type IN ('OCCURRENCE_ACKNOWLEDGED',
    'OCCURRENCE_STATUS_CHANGED', 'CAREGIVER_CHANGED', 'NO_SHOW_REPORTED', 'SERVICE_ADDRESS_CHANGED'));

-- Tên CHECK cột notifications.type do PostgreSQL tự sinh — kiểm lại bằng \d notifications trước khi chạy.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN ('BOOKING_CREATED','BOOKING_ACCEPTED',
    'BOOKING_REJECTED','BOOKING_CONFIRMED','BOOKING_CANCELLED','OCCURRENCE_CANCELLED','CAREGIVER_ASSIGNED',
    'CAREGIVER_UNASSIGNED','SESSION_STARTED','SESSION_ENDED','REPORT_SUBMITTED','OCCURRENCE_MISSED','NO_SHOW_REPORTED',
    'BOOKING_COMPLETED','PAYMENT_SUCCEEDED','REFUND_COMPLETED','REMAINING_DUE','REMAINING_OVERDUE','COMPLAINT_UPDATED',
    'REVIEW_CREATED','REVIEW_REPLIED','SETTLEMENT_UPDATED','CENTER_ACTIVATED','SHIFT_REMINDER','RECIPIENT_INVITATION',
    'BOOKING_EXPIRED','OUTBOX_FAILED','NO_SHOW_AUTO_ACCEPTED','SESSION_AUTO_CLOSED','CAREGIVER_REQUEST',
    'BOOKING_HANDED_OVER','BOOKING_ADDRESS_CHANGED'));

COMMIT;
