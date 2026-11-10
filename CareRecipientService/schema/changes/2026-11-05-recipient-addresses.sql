-- 2026-11-05 — Sổ địa chỉ của người được chăm sóc (chọn lúc đặt lịch / khi đổi địa chỉ của một số buổi).
-- Idempotent, áp tay (không Flyway).
BEGIN;

CREATE TABLE IF NOT EXISTS recipient_addresses (
    address_id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL REFERENCES care_recipients(recipient_id),
    label varchar(100) NOT NULL,
    address_line varchar(255) NOT NULL,
    ward varchar(100),
    district varchar(100),
    province varchar(100) NOT NULL,
    note varchar(500),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_recipient_addresses_recipient ON recipient_addresses (recipient_id) WHERE deleted_at IS NULL;

COMMIT;
