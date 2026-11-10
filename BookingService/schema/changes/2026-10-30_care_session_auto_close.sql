-- Đợt 4 (2026-10-30, D21 / H-10): ca quên check-out được hệ thống tự đóng — đánh dấu để manager phân biệt.
BEGIN;
ALTER TABLE care_sessions ADD COLUMN IF NOT EXISTS auto_closed boolean NOT NULL DEFAULT false;
COMMIT;
