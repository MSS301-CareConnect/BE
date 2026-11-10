-- OPS-01 (2026-10-22): caregiver xác nhận nhận ca + nhật ký sự kiện mức buổi.
-- Áp tay một lần cho DB BookingService đã có dữ liệu: psql -v ON_ERROR_STOP=1 -f schema/changes/2026-10-22_occurrence_acknowledge.sql
BEGIN;

ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS acknowledged_at timestamptz;

ALTER TABLE booking_occurrences DROP CONSTRAINT ck_booking_occurrences_status;
ALTER TABLE booking_occurrences ADD CONSTRAINT ck_booking_occurrences_status CHECK (status IN (
    'SCHEDULED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'MISSED', 'CUSTOMER_NO_SHOW'));

CREATE TABLE IF NOT EXISTS booking_events (
    event_id          uuid PRIMARY KEY,
    booking_id        uuid         NOT NULL REFERENCES bookings (booking_id),
    occurrence_id     uuid         REFERENCES booking_occurrences (occurrence_id),
    event_type        varchar(40)  NOT NULL,
    old_caregiver_id  uuid,
    new_caregiver_id  uuid,
    from_status       varchar(30),
    to_status         varchar(30),
    changed_by        uuid,
    reason            text,
    created_at        timestamptz  NOT NULL,

    CONSTRAINT ck_booking_events_type CHECK (event_type IN ('OCCURRENCE_ACKNOWLEDGED', 'OCCURRENCE_STATUS_CHANGED',
                                                            'CAREGIVER_CHANGED'))
);

CREATE INDEX IF NOT EXISTS idx_booking_events_booking ON booking_events (booking_id, created_at);

COMMIT;
