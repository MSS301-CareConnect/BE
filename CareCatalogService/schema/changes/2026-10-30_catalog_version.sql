-- Đợt 6 (2026-10-30, M-04): khóa lạc quan cho danh mục chuẩn, dịch vụ trung tâm và bảng giá.
BEGIN;
ALTER TABLE care_services ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE center_services ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE price_plans ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
COMMIT;
