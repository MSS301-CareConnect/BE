-- Đợt 6 (2026-10-30, M-05): unique toàn cục xung đột xóa mềm — trung tâm / hồ sơ caregiver đã lưu trữ giữ email /
-- user_id khiến không tạo lại được. Đổi sang partial unique index chỉ áp cho dòng chưa xóa mềm.
BEGIN;
ALTER TABLE care_centers DROP CONSTRAINT IF EXISTS uk_care_centers_email;
CREATE UNIQUE INDEX IF NOT EXISTS uk_care_centers_email_active ON care_centers (email) WHERE deleted_at IS NULL;
ALTER TABLE caregiver_profiles DROP CONSTRAINT IF EXISTS uk_caregiver_profiles_user;
CREATE UNIQUE INDEX IF NOT EXISTS uk_caregiver_profiles_user_active ON caregiver_profiles (user_id) WHERE deleted_at IS NULL;
COMMIT;
