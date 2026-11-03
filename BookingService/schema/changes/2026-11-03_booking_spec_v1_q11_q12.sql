-- Đặc tả Booking v1.0 — Q11 và Q12 (docs/2026-11-02-BOOKING_FLOW_REVIEW.md §12) cho DB ĐÃ CÓ dữ liệu. DB mới dùng
-- schema/booking.sql. Áp SAU 2026-11-02_booking_spec_v1.sql và 2026-11-02_booking_spec_v1_payment.sql:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-03_booking_spec_v1_q11_q12.sql
-- Thay đổi:
--   * Q11: booking_details.quantity / units_per_occurrence sang numeric (HOUR = phút / 60, 90 phút = 1,5 giờ). Dòng cũ
--     giữ nguyên giá trị nguyên đã tính (làm tròn lên giờ) — tiền của booking cũ không đổi.
--   * Q12: bookings.check_in_early_minutes / check_in_late_minutes — cửa sổ check-in chụp từ center policy lúc tạo.
--     Booking cũ nhận 30 / 30 (đúng cửa sổ cố định trước đây).
BEGIN;

ALTER TABLE booking_details ALTER COLUMN quantity TYPE numeric(12,4) USING quantity::numeric(12,4);
ALTER TABLE booking_details ALTER COLUMN units_per_occurrence TYPE numeric(10,4)
    USING units_per_occurrence::numeric(10,4);

ALTER TABLE bookings ADD COLUMN check_in_early_minutes integer NOT NULL DEFAULT 30;
ALTER TABLE bookings ADD COLUMN check_in_late_minutes integer NOT NULL DEFAULT 30;
ALTER TABLE bookings ALTER COLUMN check_in_early_minutes DROP DEFAULT;
ALTER TABLE bookings ALTER COLUMN check_in_late_minutes DROP DEFAULT;
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_check_in_window CHECK (check_in_early_minutes BETWEEN 0 AND 1440
    AND check_in_late_minutes BETWEEN 0 AND 1440);

COMMIT;
