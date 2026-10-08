-- Chỉ dùng khi database đã được tạo bằng ProviderService bản trước.
-- Database mới chỉ cần chạy provider.sql.

BEGIN;

ALTER TABLE care_centers ADD COLUMN IF NOT EXISTS manager_user_id uuid;

CREATE UNIQUE INDEX IF NOT EXISTS uk_care_centers_manager_active
    ON care_centers (manager_user_id)
    WHERE deleted_at IS NULL AND manager_user_id IS NOT NULL;

ALTER TABLE certificates
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

ALTER TABLE shift_registrations
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

ALTER TABLE caregiver_schedules
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

COMMIT;
