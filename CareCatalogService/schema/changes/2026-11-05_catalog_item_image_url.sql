-- Bổ sung trường image_url cho danh mục dịch vụ chuẩn (care_services)
BEGIN;
ALTER TABLE care_services ADD COLUMN IF NOT EXISTS image_url VARCHAR(1000);
COMMIT;
