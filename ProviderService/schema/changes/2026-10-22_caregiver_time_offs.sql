-- PRV-02: ngày nghỉ caregiver. Áp tay một lần cho DB Provider đã có dữ liệu:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-10-22_caregiver_time_offs.sql
-- Bảng mới, không backfill.
BEGIN;

CREATE TABLE IF NOT EXISTS caregiver_time_offs
(
    time_off_id  uuid PRIMARY KEY,
    caregiver_id uuid        NOT NULL
        REFERENCES caregiver_profiles (caregiver_id),
    start_at     timestamptz NOT NULL,
    end_at       timestamptz NOT NULL,
    reason       varchar(500),
    created_by   uuid        NOT NULL,
    version      bigint      NOT NULL DEFAULT 0
        CHECK (version >= 0),

    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    deleted_at   timestamptz,

    CONSTRAINT caregiver_time_offs_range CHECK (end_at > start_at)
);

CREATE INDEX IF NOT EXISTS idx_caregiver_time_offs_caregiver_start
    ON caregiver_time_offs (caregiver_id, start_at);

COMMIT;
