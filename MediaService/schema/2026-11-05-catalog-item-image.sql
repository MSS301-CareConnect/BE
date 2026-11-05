-- Thêm mục đích CATALOG_ITEM_IMAGE (ảnh minh họa dịch vụ chuẩn do CareCatalogService tải lên).
-- DB tạo mới đã có trong media.sql; DB đang chạy áp script này một lần.
BEGIN;
ALTER TABLE media_files DROP CONSTRAINT IF EXISTS ck_media_files_purpose;
ALTER TABLE media_files ADD CONSTRAINT ck_media_files_purpose CHECK (media_purpose IN (
    'USER_AVATAR',
    'CARE_CENTER_LOGO',
    'CATALOG_ITEM_IMAGE',
    'CAREGIVER_AVATAR',
    'CAREGIVER_CERTIFICATE',
    'CARE_RECIPIENT_AVATAR',
    'CARE_RECIPIENT_DOCUMENT',
    'CARE_REPORT_IMAGE',
    'CARE_REPORT_VIDEO',
    'INCIDENT_EVIDENCE',
    'FEEDBACK_ATTACHMENT',
    'CHAT_ATTACHMENT'
));
COMMIT;
