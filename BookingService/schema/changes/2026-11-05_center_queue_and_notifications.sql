-- Hàng đợi trung tâm + hết hạn xác nhận + thông báo (2026-11-05) cho DB ĐÃ CÓ dữ liệu. DB mới dùng schema/booking.sql.
-- Áp SAU 2026-11-03_booking_change_request_v2.sql:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-05_center_queue_and_notifications.sql
-- Thay đổi (chỉ thêm; điền expired_reason cho booking EXPIRED cũ từ lịch sử trạng thái):
--   * bookings.expired_reason: bên chưa làm kịp khi EXPIRED (trung tâm / caregiver / phân công / khoản đầu / phần còn lại).
--   * bookings.center_reminder_minutes: mốc nhắc trung tâm trước hạn xác nhận / phân công đã gửi.
--   * Thông báo mới CENTER_ACTION_DUE (nhắc trung tâm sắp quá hạn).
BEGIN;

ALTER TABLE bookings ADD COLUMN center_reminder_minutes integer;
ALTER TABLE bookings ADD COLUMN expired_reason varchar(40);
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_expired_reason CHECK (expired_reason IS NULL OR expired_reason IN (
    'CENTER_CONFIRMATION_TIMEOUT', 'CAREGIVER_CONFIRMATION_TIMEOUT', 'ASSIGNMENT_TIMEOUT', 'INITIAL_PAYMENT_TIMEOUT',
    'PAYMENT_OVERDUE'));

-- Booking EXPIRED cũ: lý do suy từ trạng thái ngay trước khi hết hạn (dòng lịch sử → EXPIRED mới nhất).
UPDATE bookings b SET expired_reason = CASE h.from_status
        WHEN 'PENDING_CENTER_CONFIRMATION' THEN 'CENTER_CONFIRMATION_TIMEOUT'
        WHEN 'PENDING_CAREGIVER_CONFIRMATION' THEN 'CAREGIVER_CONFIRMATION_TIMEOUT'
        WHEN 'PENDING_CAREGIVER_ASSIGNMENT' THEN 'ASSIGNMENT_TIMEOUT'
        WHEN 'AWAITING_INITIAL_PAYMENT' THEN 'INITIAL_PAYMENT_TIMEOUT'
        ELSE 'PAYMENT_OVERDUE' END
FROM (SELECT DISTINCT ON (booking_id) booking_id, from_status
      FROM booking_status_history WHERE to_status = 'EXPIRED' ORDER BY booking_id, changed_at DESC) h
WHERE b.booking_id = h.booking_id AND b.status = 'EXPIRED' AND b.expired_reason IS NULL;

CREATE INDEX idx_bookings_expired_reason ON bookings (center_id, created_at) WHERE expired_reason IS NOT NULL;

-- Tên CHECK cột notifications.type do PostgreSQL tự sinh — kiểm lại bằng \d notifications trước khi chạy.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN ('BOOKING_CREATED','BOOKING_ACCEPTED',
    'BOOKING_REJECTED','BOOKING_CONFIRMED','BOOKING_CANCELLED','OCCURRENCE_CANCELLED','CAREGIVER_ASSIGNED',
    'CAREGIVER_UNASSIGNED','SESSION_STARTED','SESSION_ENDED','REPORT_SUBMITTED','OCCURRENCE_MISSED','NO_SHOW_REPORTED',
    'BOOKING_COMPLETED','PAYMENT_SUCCEEDED','REFUND_COMPLETED','REMAINING_DUE','REMAINING_OVERDUE','COMPLAINT_UPDATED',
    'REVIEW_CREATED','REVIEW_REPLIED','SETTLEMENT_UPDATED','CENTER_ACTIVATED','SHIFT_REMINDER','RECIPIENT_INVITATION',
    'BOOKING_EXPIRED','OUTBOX_FAILED','NO_SHOW_AUTO_ACCEPTED','SESSION_AUTO_CLOSED','CAREGIVER_REQUEST',
    'BOOKING_HANDED_OVER','BOOKING_ADDRESS_CHANGED','CENTER_ACTION_DUE'));

COMMIT;
