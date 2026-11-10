-- 2026-11-09 — caregiver xin hủy buổi được giao (docs/2026-11-09-BOOKING_HOLD_AND_CAREGIVER_WITHDRAWAL.md) cho DB ĐÃ CÓ dữ liệu.
-- Áp SAU 2026-11-05_status_history_caregiver_confirmation.sql:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-09_caregiver_withdrawals.sql
-- Thay đổi:
--   * Bảng caregiver_withdrawals: mỗi dòng = caregiver xin hủy một buổi (trước giờ bắt đầu ≥ 24h) kèm lý do. Buổi được đánh
--     cờ needs_reassignment để Center Manager phân người khác; trạng thái xử lý suy ra từ buổi (đã đổi người / đã kết thúc).
--   * booking_events: thêm loại CAREGIVER_WITHDRAWN.
--   * bookings: auto_assign_attempted_at, unassigned_notified_at (tự phân công caregiver).
-- Giữ chỗ chờ cọc 5 phút / cọc ngay khi đặt không cần đổi schema (cấu hình booking.schedule.payment-hold).
BEGIN;

CREATE TABLE IF NOT EXISTS caregiver_withdrawals (
    withdrawal_id      uuid         PRIMARY KEY,
    booking_id         uuid         NOT NULL REFERENCES bookings (booking_id),
    occurrence_id      uuid         NOT NULL REFERENCES booking_occurrences (occurrence_id),
    center_id          uuid         NOT NULL,
    caregiver_id       uuid         NOT NULL,
    caregiver_user_id  uuid         NOT NULL,
    reason_code        varchar(20)  NOT NULL,
    reason_detail      text,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    deleted_at         timestamptz,
    CONSTRAINT ck_caregiver_withdrawals_reason CHECK (reason_code IN ('PERSONAL', 'OTHER')),
    CONSTRAINT ck_caregiver_withdrawals_detail CHECK (reason_code <> 'OTHER' OR btrim(coalesce(reason_detail, '')) <> '')
);

-- Một caregiver chỉ xin hủy một buổi một lần (hai request song song: request thua vi phạm unique).
CREATE UNIQUE INDEX IF NOT EXISTS uk_caregiver_withdrawals_occurrence
    ON caregiver_withdrawals (occurrence_id, caregiver_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_caregiver_withdrawals_center ON caregiver_withdrawals (center_id, created_at);
CREATE INDEX IF NOT EXISTS idx_caregiver_withdrawals_caregiver ON caregiver_withdrawals (caregiver_user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_caregiver_withdrawals_booking ON caregiver_withdrawals (booking_id);

-- Tự phân công caregiver (yêu cầu B): mốc thử gần nhất + mốc báo trung tâm gần nhất (scheduler thử lại / nhắc có giới hạn).
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS auto_assign_attempted_at timestamptz;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS unassigned_notified_at timestamptz;

ALTER TABLE booking_events DROP CONSTRAINT IF EXISTS ck_booking_events_type;
ALTER TABLE booking_events ADD CONSTRAINT ck_booking_events_type CHECK (event_type IN (
    'OCCURRENCE_ACKNOWLEDGED', 'OCCURRENCE_STATUS_CHANGED', 'CAREGIVER_CHANGED',
    'NO_SHOW_REPORTED', 'SERVICE_ADDRESS_CHANGED',
    'CAREGIVER_CHANGE_REQUESTED', 'CAREGIVER_CHANGE_APPROVED', 'CAREGIVER_CHANGE_REJECTED',
    'EMERGENCY_REPLACEMENT_ASSIGNED', 'OCCURRENCE_RESCHEDULED', 'CAREGIVER_WITHDRAWN'
));

COMMIT;
