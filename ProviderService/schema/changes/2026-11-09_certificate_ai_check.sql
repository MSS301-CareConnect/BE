-- 2026-11-09 — Kiểm tra chứng chỉ bằng AI (bước 4 của luồng duyệt chứng chỉ). CHỈ THÊM, chạy được nhiều lần.
-- Chạy trên database CARECONNECT_PROVIDER trước khi deploy bản ProviderService có POST /certificates/{id}/ai-check.
-- Center Manager xác nhận → ai_check_status = QUEUED; CertificateAiCheckScheduler gọi AIService và lưu kết quả MỚI NHẤT.
-- Chỉ ADMIN thấy mức rủi ro / cảnh báo / nội dung AI đọc được.

ALTER TABLE certificates ADD COLUMN IF NOT EXISTS ai_check_id uuid;
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS ai_check_status varchar(20);
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS ai_check_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS ai_next_attempt_at timestamptz;
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS ai_risk_level varchar(20);
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS ai_warnings jsonb;
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS ai_extracted jsonb;
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS ai_model varchar(120);
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS ai_checked_at timestamptz;
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS ai_last_error varchar(100);

-- Gộp nhánh 2026-11-09: bản thử 2026-11-08_certificate_ai_check.sql (đã bỏ) tạo ai_warnings kiểu text. Database nào đã
-- chạy bản đó thì đổi cột về jsonb (dữ liệu cũ không cùng định dạng nên bỏ; các cột ai_* khác của bản đó không còn dùng).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'certificates' AND column_name = 'ai_warnings' AND data_type = 'text') THEN
        ALTER TABLE certificates ALTER COLUMN ai_warnings TYPE jsonb USING NULL;
    END IF;
END $$;

ALTER TABLE certificates DROP CONSTRAINT IF EXISTS certificates_ai_check_status_check;
ALTER TABLE certificates ADD CONSTRAINT certificates_ai_check_status_check
    CHECK (ai_check_status IS NULL OR ai_check_status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED'));
ALTER TABLE certificates DROP CONSTRAINT IF EXISTS certificates_ai_risk_level_check;
ALTER TABLE certificates ADD CONSTRAINT certificates_ai_risk_level_check
    CHECK (ai_risk_level IS NULL OR ai_risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'UNDETERMINED'));
ALTER TABLE certificates DROP CONSTRAINT IF EXISTS certificates_ai_check_attempts_check;
ALTER TABLE certificates ADD CONSTRAINT certificates_ai_check_attempts_check CHECK (ai_check_attempts >= 0);

-- Scheduler quét chứng chỉ đến lượt kiểm tra (QUEUED, hoặc PROCESSING quá hạn lease).
CREATE INDEX IF NOT EXISTS idx_certificates_ai_check_due
    ON certificates (ai_check_status, ai_next_attempt_at)
    WHERE deleted_at IS NULL AND ai_check_status IN ('QUEUED', 'PROCESSING');

-- Chứng chỉ đã được trung tâm xác nhận trước bản này: xếp hàng kiểm tra AI để admin có kết quả.
UPDATE certificates
SET ai_check_id = gen_random_uuid(), ai_check_status = 'QUEUED', ai_check_attempts = 0, ai_next_attempt_at = now()
WHERE deleted_at IS NULL AND verification_status = 'CENTER_VERIFIED' AND ai_check_status IS NULL;
