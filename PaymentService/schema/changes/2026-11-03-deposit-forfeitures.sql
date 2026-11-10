-- Yêu cầu thay đổi booking v2 — CR-4 (2026-11-03, docs/2026-11-03-BOOKING_CHANGE_REQUEST_V2.md) cho DB ĐÃ CÓ dữ liệu.
-- DB mới dùng schema/payment.sql. Áp SAU 2026-11-02-payment-spec-v1.sql:
-- Chạy lại được.
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-03-deposit-forfeitures.sql
-- Thay đổi (chỉ thêm):
--   * occurrence_financials.caregiver_id / caregiver_user_id — caregiver của buổi (dòng cũ NULL → phần bị giữ thuộc trung tâm).
--   * deposit_forfeitures — sổ cọc khách bị giữ theo từng buổi, bên hưởng CENTER / CAREGIVER.
--   Phần cọc bị giữ TRƯỚC script này không được ghi sổ (vẫn nằm trong doanh thu trung tâm như cũ).
BEGIN;

ALTER TABLE occurrence_financials ADD COLUMN IF NOT EXISTS caregiver_id uuid;
ALTER TABLE occurrence_financials ADD COLUMN IF NOT EXISTS caregiver_user_id uuid;
ALTER TABLE occurrence_financials DROP CONSTRAINT IF EXISTS ck_occurrence_financials_caregiver_pair;
ALTER TABLE occurrence_financials ADD CONSTRAINT ck_occurrence_financials_caregiver_pair
    CHECK ((caregiver_id IS NULL) = (caregiver_user_id IS NULL));

CREATE TABLE IF NOT EXISTS deposit_forfeitures (
    forfeiture_id       uuid          PRIMARY KEY,
    booking_id          uuid          NOT NULL REFERENCES booking_financials(booking_id),
    center_id           uuid          NOT NULL,
    occurrence_id       uuid          NOT NULL REFERENCES occurrence_financials(occurrence_id),
    occurrence_start_at timestamptz   NOT NULL,
    payment_id          uuid          NOT NULL REFERENCES payments(payment_id),
    amount              numeric(14,2) NOT NULL CHECK (amount > 0),
    beneficiary         varchar(20)   NOT NULL CONSTRAINT ck_deposit_forfeitures_beneficiary
                                          CHECK (beneficiary IN ('CENTER','CAREGIVER')),
    caregiver_id        uuid,
    caregiver_user_id   uuid,
    reason              varchar(30)   NOT NULL,
    rule                varchar(40),
    idempotency_key     varchar(150)  NOT NULL,
    created_at          timestamptz   NOT NULL,
    CONSTRAINT ck_deposit_forfeitures_caregiver CHECK (
        (beneficiary = 'CAREGIVER' AND caregiver_id IS NOT NULL AND caregiver_user_id IS NOT NULL)
        OR (beneficiary = 'CENTER' AND caregiver_id IS NULL AND caregiver_user_id IS NULL)),
    CONSTRAINT uk_deposit_forfeitures_key UNIQUE (idempotency_key, occurrence_id)
);

CREATE INDEX IF NOT EXISTS idx_deposit_forfeitures_center ON deposit_forfeitures(center_id, created_at);
CREATE INDEX IF NOT EXISTS idx_deposit_forfeitures_caregiver ON deposit_forfeitures(caregiver_user_id, created_at)
    WHERE caregiver_user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_deposit_forfeitures_payment ON deposit_forfeitures(payment_id);

COMMIT;
