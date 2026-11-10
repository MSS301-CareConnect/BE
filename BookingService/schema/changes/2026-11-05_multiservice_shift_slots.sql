-- 2026-11-05 — Đa dịch vụ theo khung giờ tách biệt, chia khung giờ trong ca (SHIFT), trừ hao 60 phút, maxBookings.
-- Plan: docs/2026-11-05-BOOKING_MULTISERVICE_SHIFT_SLOTS_PLAN.md. Idempotent, áp tay (không Flyway).
BEGIN;

-- SHIFT được chọn nhiều khung giờ trong một ca hoặc nhiều ca (nhiều ngày).
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS ck_bookings_shift_single_day;

-- Một ca giờ có thể được nhiều buổi / nhiều booking giữ (giới hạn max_bookings kiểm ở service).
DROP INDEX IF EXISTS uk_booking_occurrences_schedule_holding;
CREATE INDEX IF NOT EXISTS idx_booking_occurrences_schedule_holding ON booking_occurrences (caregiver_schedule_id)
    WHERE caregiver_schedule_id IS NOT NULL AND status IN ('RESERVED', 'SCHEDULED', 'ACKNOWLEDGED', 'IN_PROGRESS');

-- Buổi chỉ làm một dịch vụ (NULL = mọi dịch vụ của booking, như booking cũ).
ALTER TABLE booking_occurrences ADD COLUMN IF NOT EXISTS care_service_id uuid;

-- Tên + thời lượng gói giá lúc đặt.
ALTER TABLE booking_details ADD COLUMN IF NOT EXISTS plan_name varchar(150);
ALTER TABLE booking_details ADD COLUMN IF NOT EXISTS plan_duration_minutes integer;
ALTER TABLE booking_details DROP CONSTRAINT IF EXISTS ck_booking_details_plan_duration;
ALTER TABLE booking_details ADD CONSTRAINT ck_booking_details_plan_duration
    CHECK (plan_duration_minutes IS NULL OR plan_duration_minutes > 0);

COMMIT;
