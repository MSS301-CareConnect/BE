-- CAT-03 (2026-11-03): giảm giá theo % cho gói giá. Khách trả base_price × (100 − discount_percent) / 100 (làm tròn
-- đến đồng); BookingService lấy đơn giá sau giảm từ báo giá (finalPrice). Gói cũ = 0 % nên giá không đổi.
-- Áp tay một lần cho DB CareCatalogService đã có dữ liệu, TRƯỚC khi chạy bản service mới (ddl-auto: validate):
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-03_price_plan_discount.sql
BEGIN;

ALTER TABLE price_plans
    ADD COLUMN discount_percent INT NOT NULL DEFAULT 0;

ALTER TABLE price_plans
    ADD CONSTRAINT ck_price_plans_discount_percent CHECK (discount_percent BETWEEN 0 AND 90);

COMMIT;
