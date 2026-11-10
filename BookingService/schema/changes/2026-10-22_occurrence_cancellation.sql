-- BK-03 / BK-04 (2026-10-22): hủy từng buổi (ai hủy, lúc nào) + đánh dấu buổi cần đổi caregiver khi caregiver bị khóa.
-- Áp SAU 2026-10-22_occurrence_no_show.sql: psql -v ON_ERROR_STOP=1 -f schema/changes/2026-10-22_occurrence_cancellation.sql
BEGIN;

ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS cancelled_at timestamptz;
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS cancelled_by_party varchar(20);
-- Backfill an toàn: mặc định false cho mọi dòng cũ rồi mới đặt NOT NULL.
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS needs_reassignment boolean NOT NULL DEFAULT false;

ALTER TABLE booking_occurrences DROP CONSTRAINT IF EXISTS ck_booking_occurrences_cancelled_by_party;
ALTER TABLE booking_occurrences ADD CONSTRAINT ck_booking_occurrences_cancelled_by_party
    CHECK (cancelled_by_party IS NULL OR cancelled_by_party IN ('CUSTOMER', 'CENTER', 'SYSTEM', 'ADMIN'));

COMMIT;
