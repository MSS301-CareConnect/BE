-- PRV-02 / D11: caregiver_schedules chỉ còn SCHEDULED / CANCELLED (tiến độ ca thật thuộc BookingService).
-- Áp tay một lần cho DB Provider đã có dữ liệu:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-10-22_caregiver_schedule_status_d11.sql
-- Backfill IN_PROGRESS / COMPLETED → SCHEDULED TRƯỚC khi siết CHECK (nếu không ADD CONSTRAINT sẽ lỗi).
BEGIN;

UPDATE caregiver_schedules
SET schedule_status = 'SCHEDULED',
    updated_at      = now()
WHERE schedule_status IN ('IN_PROGRESS', 'COMPLETED');

-- CHECK cũ khai báo inline không tên → PostgreSQL tự đặt tên caregiver_schedules_schedule_status_check.
ALTER TABLE caregiver_schedules
    DROP CONSTRAINT IF EXISTS caregiver_schedules_schedule_status_check;

ALTER TABLE caregiver_schedules
    ADD CONSTRAINT caregiver_schedules_schedule_status_check
        CHECK (schedule_status IN ('SCHEDULED', 'CANCELLED'));

COMMIT;
