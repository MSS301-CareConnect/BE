-- BK-01 (2026-10-22): giá từng buổi + số đơn vị mỗi buổi.
-- Áp tay một lần cho DB BookingService đã có dữ liệu: psql -v ON_ERROR_STOP=1 -f schema/changes/2026-10-22_occurrence_price.sql
-- Booking tạo trước đó giữ NULL (không backfill giá đoán); ràng buộc "phải có giá" chỉ áp ở service cho booking mới.
BEGIN;

ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS price_amount numeric(14,2);
ALTER TABLE booking_occurrences
    ADD CONSTRAINT ck_booking_occurrences_price CHECK (price_amount IS NULL OR price_amount >= 0);

ALTER TABLE booking_details ADD COLUMN IF NOT EXISTS units_per_occurrence integer;
ALTER TABLE booking_details
    ADD CONSTRAINT ck_booking_details_units CHECK (units_per_occurrence IS NULL OR units_per_occurrence > 0);

COMMIT;
