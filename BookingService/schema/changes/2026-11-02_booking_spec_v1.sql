-- Đặc tả nghiệp vụ Booking v1.0 (2026-11-02, docs/2026-11-02-BOOKING_FLOW_REVIEW.md đợt 1) cho DB ĐÃ CÓ dữ liệu.
-- DB mới dùng schema/booking.sql. Áp SAU mọi change script trước đó:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-02_booking_spec_v1.sql
-- Thay đổi:
--   * bookings.schedule_type (ONE_TIME / DAILY / WEEKLY) → booking_type (SHIFT / RECURRING / CUSTOM) + recurrence_frequency.
--     ONE_TIME cũ → CUSTOM một buổi (giờ nằm ở booking_occurrences, start_time / end_time của booking để NULL);
--     DAILY / WEEKLY → RECURRING cùng tần suất.
--   * Trạng thái booking theo spec §11.1: PENDING → PENDING_CENTER_CONFIRMATION, AWAITING_DEPOSIT → AWAITING_INITIAL_PAYMENT,
--     thêm PENDING_CAREGIVER_ASSIGNMENT (cả trong booking_status_history).
--   * Trạng thái buổi theo spec §11.2: MISSED → CAREGIVER_MISSED; thêm PENDING_ASSIGNMENT, RESERVED, EXPIRED. Buổi SCHEDULED
--     của booking chưa trả khoản đầu → RESERVED (có caregiver) / PENDING_ASSIGNMENT (chưa có).
--   * booking_occurrences.availability_slot_id → caregiver_schedule_id (SHIFT) + unique một lịch một buổi đang giữ.
--   * Cột mới: initial_payment_percent (booking cũ là luồng đặt cọc → 30), assignment_due_at, remaining_payment_due_at.
--     remaining_payment_due_at của booking cũ để NULL: chúng theo quy tắc cũ (hóa đơn phần còn lại sau khi hoàn tất).
--   * Bảng center_booking_policies (spec §8).
BEGIN;

-- ─── bookings ────────────────────────────────────────────────────────────────
ALTER TABLE bookings ADD COLUMN booking_type varchar(20);
ALTER TABLE bookings ADD COLUMN recurrence_frequency varchar(20);
ALTER TABLE bookings ADD COLUMN initial_payment_percent numeric(5,2);
ALTER TABLE bookings ADD COLUMN assignment_due_at timestamptz;
ALTER TABLE bookings ADD COLUMN remaining_payment_due_at timestamptz;

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_schedule_type;
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_one_time_single_day;
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_time_range;
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_days_of_week;
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_status;
ALTER TABLE bookings ALTER COLUMN start_time DROP NOT NULL;
ALTER TABLE bookings ALTER COLUMN end_time DROP NOT NULL;

UPDATE bookings
SET booking_type = CASE schedule_type WHEN 'ONE_TIME' THEN 'CUSTOM' ELSE 'RECURRING' END,
    recurrence_frequency = CASE schedule_type WHEN 'ONE_TIME' THEN NULL ELSE schedule_type END,
    start_time = CASE schedule_type WHEN 'ONE_TIME' THEN NULL ELSE start_time END,
    end_time = CASE schedule_type WHEN 'ONE_TIME' THEN NULL ELSE end_time END,
    initial_payment_percent = 30;

UPDATE bookings SET status = 'PENDING_CENTER_CONFIRMATION' WHERE status = 'PENDING';
UPDATE bookings SET status = 'AWAITING_INITIAL_PAYMENT' WHERE status = 'AWAITING_DEPOSIT';

ALTER TABLE bookings ALTER COLUMN booking_type SET NOT NULL;
ALTER TABLE bookings ALTER COLUMN initial_payment_percent SET NOT NULL;
ALTER TABLE bookings DROP COLUMN schedule_type;

ALTER TABLE bookings ADD CONSTRAINT ck_bookings_booking_type CHECK (booking_type IN ('SHIFT', 'RECURRING', 'CUSTOM'));
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_recurrence_frequency CHECK (
    (booking_type = 'RECURRING' AND recurrence_frequency IN ('DAILY', 'WEEKLY'))
    OR (booking_type <> 'RECURRING' AND recurrence_frequency IS NULL));
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING_CENTER_CONFIRMATION',
    'PENDING_CAREGIVER_ASSIGNMENT', 'AWAITING_INITIAL_PAYMENT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED',
    'CANCELLED', 'EXPIRED'));
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_shift_single_day CHECK (booking_type <> 'SHIFT' OR end_date = start_date);
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_time_range CHECK (
    (booking_type = 'RECURRING' AND start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
    OR (booking_type <> 'RECURRING' AND start_time IS NULL AND end_time IS NULL));
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_days_of_week CHECK (
    (recurrence_frequency = 'WEEKLY'
        AND days_of_week IS NOT NULL
        AND cardinality(days_of_week) BETWEEN 1 AND 7
        AND days_of_week <@ ARRAY[1, 2, 3, 4, 5, 6, 7]::smallint[])
    OR (recurrence_frequency IS DISTINCT FROM 'WEEKLY' AND days_of_week IS NULL));
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_initial_payment_percent CHECK (
    initial_payment_percent > 0 AND initial_payment_percent <= 100);

DROP INDEX IF EXISTS idx_bookings_acceptance_due;
DROP INDEX IF EXISTS idx_bookings_deposit_due;
CREATE INDEX idx_bookings_acceptance_due ON bookings (acceptance_due_at) WHERE status = 'PENDING_CENTER_CONFIRMATION';
CREATE INDEX idx_bookings_assignment_due ON bookings (assignment_due_at) WHERE status = 'PENDING_CAREGIVER_ASSIGNMENT';
CREATE INDEX idx_bookings_deposit_due ON bookings (deposit_due_at) WHERE status = 'AWAITING_INITIAL_PAYMENT';

-- ─── booking_status_history ──────────────────────────────────────────────────
ALTER TABLE booking_status_history DROP CONSTRAINT IF EXISTS ck_booking_status_history_from;
ALTER TABLE booking_status_history DROP CONSTRAINT IF EXISTS ck_booking_status_history_to;
UPDATE booking_status_history SET from_status = 'PENDING_CENTER_CONFIRMATION' WHERE from_status = 'PENDING';
UPDATE booking_status_history SET from_status = 'AWAITING_INITIAL_PAYMENT' WHERE from_status = 'AWAITING_DEPOSIT';
UPDATE booking_status_history SET to_status = 'PENDING_CENTER_CONFIRMATION' WHERE to_status = 'PENDING';
UPDATE booking_status_history SET to_status = 'AWAITING_INITIAL_PAYMENT' WHERE to_status = 'AWAITING_DEPOSIT';
ALTER TABLE booking_status_history ADD CONSTRAINT ck_booking_status_history_from CHECK (from_status IS NULL OR from_status IN (
    'PENDING_CENTER_CONFIRMATION', 'PENDING_CAREGIVER_ASSIGNMENT', 'AWAITING_INITIAL_PAYMENT', 'CONFIRMED',
    'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED'));
ALTER TABLE booking_status_history ADD CONSTRAINT ck_booking_status_history_to CHECK (to_status IN (
    'PENDING_CENTER_CONFIRMATION', 'PENDING_CAREGIVER_ASSIGNMENT', 'AWAITING_INITIAL_PAYMENT', 'CONFIRMED',
    'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED'));

-- ─── booking_occurrences ─────────────────────────────────────────────────────
ALTER TABLE booking_occurrences RENAME COLUMN availability_slot_id TO caregiver_schedule_id;
ALTER TABLE booking_occurrences DROP CONSTRAINT IF EXISTS ck_booking_occurrences_status;
ALTER TABLE booking_occurrences DROP CONSTRAINT IF EXISTS ex_booking_occurrences_caregiver_slot;

UPDATE booking_occurrences SET status = 'CAREGIVER_MISSED' WHERE status = 'MISSED';
-- Booking chưa trả khoản đầu: buổi chỉ đang giữ lịch (RESERVED) hoặc chờ phân caregiver.
UPDATE booking_occurrences o
SET status = CASE WHEN o.caregiver_id IS NULL THEN 'PENDING_ASSIGNMENT' ELSE 'RESERVED' END
FROM bookings b
WHERE b.booking_id = o.booking_id
  AND b.status IN ('PENDING_CENTER_CONFIRMATION', 'AWAITING_INITIAL_PAYMENT')
  AND o.status = 'SCHEDULED';
UPDATE booking_events SET from_status = 'CAREGIVER_MISSED' WHERE from_status = 'MISSED';
UPDATE booking_events SET to_status = 'CAREGIVER_MISSED' WHERE to_status = 'MISSED';

ALTER TABLE booking_occurrences ADD CONSTRAINT ck_booking_occurrences_status CHECK (status IN ('PENDING_ASSIGNMENT',
    'RESERVED', 'SCHEDULED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'CUSTOMER_NO_SHOW',
    'CAREGIVER_MISSED'));
ALTER TABLE booking_occurrences ADD CONSTRAINT ck_booking_occurrences_pending_assignment CHECK (
    status <> 'PENDING_ASSIGNMENT' OR caregiver_id IS NULL);
ALTER TABLE booking_occurrences ADD CONSTRAINT ex_booking_occurrences_caregiver_slot
    EXCLUDE USING gist (caregiver_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
    WHERE (status IN ('RESERVED', 'SCHEDULED', 'ACKNOWLEDGED', 'IN_PROGRESS'));
CREATE UNIQUE INDEX uk_booking_occurrences_schedule_holding ON booking_occurrences (caregiver_schedule_id)
    WHERE caregiver_schedule_id IS NOT NULL AND status IN ('RESERVED', 'SCHEDULED', 'ACKNOWLEDGED', 'IN_PROGRESS');

-- ─── center_booking_policies (spec §8) ───────────────────────────────────────
CREATE TABLE center_booking_policies (
    policy_id                              uuid PRIMARY KEY,
    center_id                              uuid          NOT NULL,
    shift_min_advance_minutes              integer       NOT NULL,
    recurring_min_advance_minutes          integer       NOT NULL,
    custom_min_advance_minutes             integer       NOT NULL,
    confirmation_timeout_minutes           integer       NOT NULL,
    assignment_timeout_minutes             integer       NOT NULL,
    initial_payment_timeout_minutes        integer       NOT NULL,
    remaining_payment_due_before_minutes   integer       NOT NULL,
    remaining_payment_reminder_minutes     integer[]     NOT NULL,
    deposit_percent                        numeric(5,2)  NOT NULL,
    check_in_early_minutes                 integer       NOT NULL,
    check_in_late_minutes                  integer       NOT NULL,
    refund_threshold_minutes               integer       NOT NULL,
    refund_percent_before_threshold        numeric(5,2)  NOT NULL,
    refund_percent_after_threshold         numeric(5,2)  NOT NULL,
    overdue_payment_forfeit_percent        numeric(5,2)  NOT NULL,
    updated_by                             uuid,
    created_at                             timestamptz   NOT NULL,
    updated_at                             timestamptz   NOT NULL,
    deleted_at                             timestamptz,
    version                                bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uk_center_booking_policies_center UNIQUE (center_id),
    CONSTRAINT ck_center_booking_policies_minutes CHECK (shift_min_advance_minutes >= 0
        AND recurring_min_advance_minutes > remaining_payment_due_before_minutes
        AND custom_min_advance_minutes > remaining_payment_due_before_minutes
        AND confirmation_timeout_minutes > 0 AND assignment_timeout_minutes > 0
        AND initial_payment_timeout_minutes > 0 AND remaining_payment_due_before_minutes >= 0
        AND check_in_early_minutes >= 0 AND check_in_late_minutes >= 0 AND refund_threshold_minutes >= 0),
    CONSTRAINT ck_center_booking_policies_percents CHECK (deposit_percent > 0 AND deposit_percent <= 100
        AND refund_percent_before_threshold BETWEEN 0 AND 100
        AND refund_percent_after_threshold BETWEEN 0 AND refund_percent_before_threshold
        AND overdue_payment_forfeit_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_center_booking_policies_reminders CHECK (cardinality(remaining_payment_reminder_minutes) <= 5),
    CONSTRAINT ck_center_booking_policies_version CHECK (version >= 0)
);

COMMIT;
