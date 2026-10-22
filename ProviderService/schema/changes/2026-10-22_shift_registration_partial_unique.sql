-- PRV-03: cho phép đăng ký lại ca sau khi CANCELLED/REJECTED. Áp tay một lần cho DB Provider đã có dữ liệu:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-10-22_shift_registration_partial_unique.sql
-- Dữ liệu cũ thỏa unique toàn phần nên chắc chắn thỏa unique một phần — không cần dọn dữ liệu.
BEGIN;

ALTER TABLE shift_registrations
    DROP CONSTRAINT IF EXISTS uk_shift_registrations_shift_caregiver;

CREATE UNIQUE INDEX IF NOT EXISTS uk_shift_registrations_shift_caregiver_active
    ON shift_registrations (work_shift_id, caregiver_id)
    WHERE registration_status IN ('PENDING', 'APPROVED');

-- Lịch của đăng ký đã hủy không được chặn lịch mới khi đăng ký lại được duyệt.
ALTER TABLE caregiver_schedules
    DROP CONSTRAINT IF EXISTS uk_caregiver_schedules_shift_caregiver;

CREATE UNIQUE INDEX IF NOT EXISTS uk_caregiver_schedules_shift_caregiver_active
    ON caregiver_schedules (work_shift_id, caregiver_id)
    WHERE schedule_status <> 'CANCELLED';

COMMIT;
