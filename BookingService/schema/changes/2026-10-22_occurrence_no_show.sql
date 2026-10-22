-- OPS-06 (2026-10-22): job MISSED + caregiver báo khách vắng mặt (manager quyết định).
-- Áp SAU 2026-10-22_care_sessions_outbox.sql: psql -v ON_ERROR_STOP=1 -f schema/changes/2026-10-22_occurrence_no_show.sql
BEGIN;

ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS missed_reason varchar(30);
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS no_show_reported_at timestamptz;
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS no_show_reported_by uuid;
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS no_show_note text;
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS no_show_decided_by uuid;
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS no_show_decided_at timestamptz;
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS no_show_reminded_at timestamptz;

ALTER TABLE booking_occurrences DROP CONSTRAINT IF EXISTS ck_booking_occurrences_missed_reason;
ALTER TABLE booking_occurrences ADD CONSTRAINT ck_booking_occurrences_missed_reason
    CHECK (missed_reason IS NULL OR missed_reason IN ('CAREGIVER'));
ALTER TABLE booking_occurrences DROP CONSTRAINT IF EXISTS ck_booking_occurrences_no_show_pair;
ALTER TABLE booking_occurrences ADD CONSTRAINT ck_booking_occurrences_no_show_pair
    CHECK ((no_show_reported_at IS NULL) = (no_show_reported_by IS NULL));

CREATE INDEX IF NOT EXISTS idx_booking_occurrences_not_started ON booking_occurrences (start_at)
    WHERE status IN ('SCHEDULED', 'ACKNOWLEDGED');

ALTER TABLE booking_events DROP CONSTRAINT ck_booking_events_type;
ALTER TABLE booking_events ADD CONSTRAINT ck_booking_events_type CHECK (event_type IN (
    'OCCURRENCE_ACKNOWLEDGED', 'OCCURRENCE_STATUS_CHANGED', 'CAREGIVER_CHANGED', 'NO_SHOW_REPORTED'));

COMMIT;
