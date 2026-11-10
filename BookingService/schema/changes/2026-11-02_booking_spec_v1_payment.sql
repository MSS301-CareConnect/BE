-- Đặc tả Booking v1.0 đợt 4 (docs/2026-11-02-BOOKING_FLOW_REVIEW.md §9) cho DB ĐÃ CÓ dữ liệu. DB mới dùng schema/booking.sql.
-- Áp SAU 2026-11-02_booking_spec_v1.sql:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-02_booking_spec_v1_payment.sql
-- Thay đổi:
--   * bookings.fully_paid_at: PaymentService báo đã trả đủ (spec §9.1). Booking cũ để NULL — chúng không có
--     remaining_payment_due_at nên check-in không đòi trả đủ và vẫn theo quy tắc cũ (hóa đơn sau hoàn tất).
--   * bookings.remaining_reminder_minutes: mốc nhắc trả phần còn lại đã gửi (M6).
BEGIN;

ALTER TABLE bookings ADD COLUMN fully_paid_at timestamptz;
ALTER TABLE bookings ADD COLUMN remaining_reminder_minutes integer;

COMMIT;
