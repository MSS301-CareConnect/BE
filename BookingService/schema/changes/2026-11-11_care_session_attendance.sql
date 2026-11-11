-- 2026-11-11: chấm công ca chăm sóc (trễ / về sớm), cờ vị trí check-in và xác nhận của trung tâm cho ca hệ thống tự đóng.
-- Áp dụng tay vào DB booking (không Flyway). Chạy lại an toàn.

ALTER TABLE care_sessions
    ADD COLUMN IF NOT EXISTS late_minutes        integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS early_leave_minutes integer,
    ADD COLUMN IF NOT EXISTS check_in_distance_m integer,
    ADD COLUMN IF NOT EXISTS location_flag       varchar(20),
    ADD COLUMN IF NOT EXISTS completion_review   varchar(20),
    ADD COLUMN IF NOT EXISTS reviewed_by         uuid,
    ADD COLUMN IF NOT EXISTS reviewed_at         timestamptz,
    ADD COLUMN IF NOT EXISTS review_note         text;

ALTER TABLE care_sessions DROP CONSTRAINT IF EXISTS ck_care_sessions_location_flag;
ALTER TABLE care_sessions
    ADD CONSTRAINT ck_care_sessions_location_flag
        CHECK (location_flag IS NULL OR location_flag IN ('NO_LOCATION', 'OUT_OF_RANGE'));

ALTER TABLE care_sessions DROP CONSTRAINT IF EXISTS ck_care_sessions_completion_review;
ALTER TABLE care_sessions
    ADD CONSTRAINT ck_care_sessions_completion_review
        CHECK (completion_review IS NULL OR completion_review IN ('PENDING', 'CONFIRMED', 'NOT_DELIVERED'));

-- Hàng đợi "ca tự đóng chờ trung tâm xác nhận".
CREATE INDEX IF NOT EXISTS ix_care_sessions_completion_review
    ON care_sessions (completion_review) WHERE completion_review IS NOT NULL AND deleted_at IS NULL;

-- Ca tự đóng TRƯỚC thay đổi này đã được quyết toán như bình thường → không đưa vào hàng đợi (completion_review NULL).

-- notifications.type: thêm SESSION_LOCATION_FLAGGED (check-in thiếu vị trí / xa địa chỉ chăm sóc, báo trung tâm).
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'BOOKING_CREATED','BOOKING_ACCEPTED','BOOKING_REJECTED','BOOKING_CONFIRMED','BOOKING_CANCELLED',
    'OCCURRENCE_CANCELLED','CAREGIVER_ASSIGNED','CAREGIVER_UNASSIGNED','SESSION_STARTED','SESSION_ENDED',
    'REPORT_SUBMITTED','OCCURRENCE_MISSED','NO_SHOW_REPORTED','BOOKING_COMPLETED','PAYMENT_SUCCEEDED',
    'REFUND_COMPLETED','REMAINING_DUE','REMAINING_OVERDUE','COMPLAINT_UPDATED','REVIEW_CREATED',
    'REVIEW_REPLIED','SETTLEMENT_UPDATED','CENTER_ACTIVATED','SHIFT_REMINDER','RECIPIENT_INVITATION',
    'BOOKING_EXPIRED','OUTBOX_FAILED','NO_SHOW_AUTO_ACCEPTED','SESSION_AUTO_CLOSED','CAREGIVER_REQUEST',
    'BOOKING_HANDED_OVER','BOOKING_ADDRESS_CHANGED','CENTER_ACTION_DUE',
    'CAREGIVER_CHANGE_REQUEST_CREATED','CAREGIVER_CHANGE_REQUEST_DECIDED',
    'EMERGENCY_REPLACEMENT_NOTIFIED','OCCURRENCE_RESCHEDULED_NOTIFICATION',
    'CHECK_IN_LATE','CAREGIVER_ABSENCE_UPDATED',
    'WALLET_WITHDRAWAL_COMPLETED','WALLET_WITHDRAWAL_REJECTED',
    'SESSION_LOCATION_FLAGGED'));

ALTER TABLE caregiver_absences DROP CONSTRAINT IF EXISTS ck_caregiver_absences_source;
ALTER TABLE caregiver_absences
    ADD CONSTRAINT ck_caregiver_absences_source
        CHECK (source IN ('MISSED_CHECK_IN', 'NO_SHOW_REPORT_REJECTED', 'SESSION_NOT_DELIVERED'));
