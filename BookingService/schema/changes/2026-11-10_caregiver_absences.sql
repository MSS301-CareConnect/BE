-- 2026-11-10 — hồ sơ caregiver vắng ca + cảnh báo sớm chưa check-in (docs/2026-11-10-CAREGIVER_ABSENCE_AND_REFUND_REVIEW.md)
-- cho DB ĐÃ CÓ dữ liệu. Áp SAU 2026-11-09_caregiver_withdrawals.sql:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-10_caregiver_absences.sql
-- Thay đổi:
--   * Bảng caregiver_absences: mỗi buổi CAREGIVER_MISSED có caregiver = một dòng; caregiver giải trình, Center Manager kết
--     luận (EXCUSED / CONFIRMED). Tiền không nằm ở đây — khách đã được hoàn 100 % qua outbox REFUND.
--   * booking_occurrences.late_warned_at: đã cảnh báo "tới giờ mà chưa check-in" (một lần mỗi buổi).
--   * notifications.type: thêm CHECK_IN_LATE, CAREGIVER_ABSENCE_UPDATED.
--   * Backfill: buổi CAREGIVER_MISSED cũ có caregiver được ghi hồ sơ PENDING để trung tâm kết luận (chạy lại không ghi trùng).
BEGIN;

CREATE TABLE IF NOT EXISTS caregiver_absences (
    absence_id         uuid         PRIMARY KEY,
    booking_id         uuid         NOT NULL REFERENCES bookings (booking_id),
    occurrence_id      uuid         NOT NULL REFERENCES booking_occurrences (occurrence_id),
    center_id          uuid         NOT NULL,
    caregiver_id       uuid         NOT NULL,
    caregiver_user_id  uuid         NOT NULL,
    source             varchar(30)  NOT NULL,
    status             varchar(20)  NOT NULL,
    explanation        text,
    explained_at       timestamptz,
    conclusion_note    text,
    concluded_by       uuid,
    concluded_at       timestamptz,
    version            bigint       NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    deleted_at         timestamptz,
    CONSTRAINT ck_caregiver_absences_source CHECK (source IN ('MISSED_CHECK_IN', 'NO_SHOW_REPORT_REJECTED')),
    CONSTRAINT ck_caregiver_absences_status CHECK (status IN ('PENDING', 'EXCUSED', 'CONFIRMED')),
    CONSTRAINT ck_caregiver_absences_explanation CHECK ((explanation IS NULL) = (explained_at IS NULL)),
    -- Đã kết luận thì phải có thời điểm; miễn trừ thì phải có lý do.
    CONSTRAINT ck_caregiver_absences_concluded CHECK ((status = 'PENDING') = (concluded_at IS NULL)),
    CONSTRAINT ck_caregiver_absences_excused_note CHECK (
        status <> 'EXCUSED' OR btrim(coalesce(conclusion_note, '')) <> ''),
    CONSTRAINT ck_caregiver_absences_version CHECK (version >= 0)
);

-- Một buổi chỉ có một hồ sơ vắng ca (job MISSED và quyết định của manager cùng lúc: bên thua vi phạm unique).
CREATE UNIQUE INDEX IF NOT EXISTS uk_caregiver_absences_occurrence
    ON caregiver_absences (occurrence_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_caregiver_absences_center ON caregiver_absences (center_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_caregiver_absences_caregiver ON caregiver_absences (caregiver_id, created_at);
CREATE INDEX IF NOT EXISTS idx_caregiver_absences_caregiver_user ON caregiver_absences (caregiver_user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_caregiver_absences_booking ON caregiver_absences (booking_id);

ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS late_warned_at timestamptz;

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
    'CHECK_IN_LATE','CAREGIVER_ABSENCE_UPDATED'
));

-- Buổi đã CAREGIVER_MISSED trước ngày triển khai: ghi hồ sơ chờ kết luận. Buổi có báo "khách vắng" bị trung tâm bác thì
-- nguồn là NO_SHOW_REPORT_REJECTED, còn lại là job quá hạn check-in.
INSERT INTO caregiver_absences (absence_id, booking_id, occurrence_id, center_id, caregiver_id, caregiver_user_id, source,
                                status, version, created_at, updated_at)
SELECT gen_random_uuid(), o.booking_id, o.occurrence_id, b.center_id, o.caregiver_id, o.caregiver_user_id,
       CASE WHEN o.no_show_reported_at IS NOT NULL THEN 'NO_SHOW_REPORT_REJECTED' ELSE 'MISSED_CHECK_IN' END,
       'PENDING', 0, now(), now()
FROM booking_occurrences o
         JOIN bookings b ON b.booking_id = o.booking_id
WHERE o.status = 'CAREGIVER_MISSED'
  AND o.caregiver_id IS NOT NULL
  AND o.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM caregiver_absences a WHERE a.occurrence_id = o.occurrence_id AND a.deleted_at IS NULL);

COMMIT;
