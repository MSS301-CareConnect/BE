BEGIN;

ALTER TABLE certificates
    ADD COLUMN IF NOT EXISTS ai_risk_level varchar(20),
    ADD COLUMN IF NOT EXISTS ai_warnings text,
    ADD COLUMN IF NOT EXISTS ai_extracted_text text,
    ADD COLUMN IF NOT EXISTS ai_qr_content text,
    ADD COLUMN IF NOT EXISTS ai_caregiver_name_matched boolean,
    ADD COLUMN IF NOT EXISTS ai_certificate_name_matched boolean,
    ADD COLUMN IF NOT EXISTS ai_issued_by_matched boolean,
    ADD COLUMN IF NOT EXISTS ai_credential_number_matched boolean,
    ADD COLUMN IF NOT EXISTS ai_issued_date_matched boolean,
    ADD COLUMN IF NOT EXISTS ai_expiry_date_matched boolean,
    ADD COLUMN IF NOT EXISTS ai_expired boolean,
    ADD COLUMN IF NOT EXISTS ai_checked_at timestamptz;

COMMIT;