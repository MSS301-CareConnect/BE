-- PRV-01: kỹ năng caregiver (caregiver_services). Áp tay một lần cho DB Provider đã có dữ liệu:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-10-22_caregiver_services.sql
-- Bảng mới, không backfill: caregiver cũ chưa có kỹ năng (= [] — Booking từ chối mọi dịch vụ tới khi manager gán).
BEGIN;

CREATE TABLE IF NOT EXISTS caregiver_services
(
    caregiver_id      uuid        NOT NULL
        REFERENCES caregiver_profiles (caregiver_id),
    center_service_id uuid        NOT NULL,
    care_service_id   uuid        NOT NULL,
    service_name      varchar(255),
    assigned_by       uuid        NOT NULL,
    assigned_at       timestamptz NOT NULL,

    CONSTRAINT pk_caregiver_services PRIMARY KEY (caregiver_id, center_service_id)
);

CREATE INDEX IF NOT EXISTS idx_caregiver_services_care_service
    ON caregiver_services (care_service_id);

COMMIT;
