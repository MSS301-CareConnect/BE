-- Yêu cầu thay đổi booking v3.0 (2026-11-05, docs/2026-11-05-BOOKING_CHANGE_REQUEST_V3.md) cho DB ĐÃ CÓ dữ liệu.
-- Áp SAU 2026-11-05_center_queue_and_notifications.sql:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-05_booking_spec_v3_reassignment_reschedule.sql
-- Thay đổi:
--   * Bảng caregiver_change_requests: khách yêu cầu đổi người chăm sóc kèm lý do.
--   * Bảng caregiver_change_request_occurrences: danh sách buổi áp dụng đổi người.
--   * booking_occurrences: cột hỗ trợ dời lịch (reschedule) và thay thế người khẩn cấp (emergency replacement).
--   * booking_events: thêm loại sự kiện mới.
--   * notifications: thêm loại thông báo mới.
BEGIN;

-- 1. Bảng yêu cầu đổi caregiver từ khách hàng
CREATE TABLE IF NOT EXISTS caregiver_change_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(booking_id),
    customer_user_id UUID NOT NULL,
    center_id UUID NOT NULL,
    scope_type VARCHAR(30) NOT NULL,
    from_date DATE,
    reason_code VARCHAR(50) NOT NULL,
    reason_detail TEXT,
    preferred_caregiver_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_change_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_change_request_scope CHECK (scope_type IN ('ALL_REMAINING', 'SPECIFIC_OCCURRENCES', 'FROM_DATE')),
    CONSTRAINT ck_change_request_reason CHECK (reason_code IN ('SKILL_MISMATCH', 'ATTITUDE_ISSUE', 'PUNCTUALITY_ISSUE', 'PERSONAL_PREFERENCE', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_change_requests_booking ON caregiver_change_requests(booking_id);
CREATE INDEX IF NOT EXISTS idx_change_requests_center_status ON caregiver_change_requests(center_id, status);

CREATE TABLE IF NOT EXISTS caregiver_change_request_occurrences (
    request_id UUID NOT NULL REFERENCES caregiver_change_requests(id) ON DELETE CASCADE,
    occurrence_id UUID NOT NULL REFERENCES booking_occurrences(occurrence_id),
    PRIMARY KEY (request_id, occurrence_id)
);

-- 2. Cột mới cho booking_occurrences
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS reschedule_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS original_start_at TIMESTAMPTZ;
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS original_end_at TIMESTAMPTZ;
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS is_emergency_replacement BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS replacement_reason TEXT;
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS replacement_consent_status VARCHAR(30) DEFAULT 'NONE';

ALTER TABLE booking_occurrences DROP CONSTRAINT IF EXISTS ck_occurrences_consent_status;
ALTER TABLE booking_occurrences ADD CONSTRAINT ck_occurrences_consent_status 
    CHECK (replacement_consent_status IN ('NONE', 'PENDING_CUSTOMER', 'ACCEPTED', 'REJECTED_RESCHEDULE', 'REJECTED_CANCEL'));

-- 3. Cập nhật booking_events.event_type
ALTER TABLE booking_events DROP CONSTRAINT IF EXISTS ck_booking_events_type;
ALTER TABLE booking_events ADD CONSTRAINT ck_booking_events_type CHECK (event_type IN (
    'OCCURRENCE_ACKNOWLEDGED', 'OCCURRENCE_STATUS_CHANGED', 'CAREGIVER_CHANGED', 
    'NO_SHOW_REPORTED', 'SERVICE_ADDRESS_CHANGED',
    'CAREGIVER_CHANGE_REQUESTED', 'CAREGIVER_CHANGE_APPROVED', 'CAREGIVER_CHANGE_REJECTED',
    'EMERGENCY_REPLACEMENT_ASSIGNED', 'OCCURRENCE_RESCHEDULED'
));

-- 4. Cập nhật notifications.type
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
    'EMERGENCY_REPLACEMENT_NOTIFIED','OCCURRENCE_RESCHEDULED_NOTIFICATION'
));

COMMIT;
