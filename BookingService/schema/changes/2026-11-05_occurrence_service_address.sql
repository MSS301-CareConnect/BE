-- 2026-11-05 — Địa chỉ chăm sóc theo từng buổi (khách chọn buổi cần đổi, buổi còn dưới 24h giữ nguyên).
-- NULL = dùng địa chỉ của booking (bookings.service_address). Idempotent, áp tay (không Flyway).
BEGIN;

ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS service_address text;

COMMIT;
