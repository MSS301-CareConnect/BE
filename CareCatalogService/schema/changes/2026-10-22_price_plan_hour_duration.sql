-- CAT-01 (2026-10-22): bảng giá theo giờ (HOUR) luôn tính trên 60 phút (Booking nhân đơn giá với số giờ của buổi).
-- Áp tay một lần cho DB CareCatalogService đã có dữ liệu:
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-10-22_price_plan_hour_duration.sql
-- Plan HOUR có duration_minutes <> 60: TẮT (is_active = false) và in danh sách để trung tâm rà lại giá, rồi chuẩn hóa
-- duration_minutes = 60 để thêm CHECK hợp lệ. Plan đã tắt không vào được báo giá (quote bắt buộc plan active) nên khách
-- không bị tính theo đơn giá cũ; manager phải sửa lại base_price theo giờ trước khi bật lại.
BEGIN;

DO $$
DECLARE
    plan RECORD;
BEGIN
    FOR plan IN
        SELECT price_id, center_service_id, plan_name, duration_minutes, base_price
        FROM price_plans
        WHERE pricing_unit = 'HOUR' AND duration_minutes <> 60
    LOOP
        RAISE NOTICE 'Deactivated HOUR price plan % (center_service %, "%", % min, base_price %)',
            plan.price_id, plan.center_service_id, plan.plan_name, plan.duration_minutes, plan.base_price;
    END LOOP;
END $$;

UPDATE price_plans
SET is_active = false,
    duration_minutes = 60,
    updated_at = CURRENT_TIMESTAMP
WHERE pricing_unit = 'HOUR' AND duration_minutes <> 60;

ALTER TABLE price_plans
    ADD CONSTRAINT ck_price_plans_hour_duration CHECK (pricing_unit <> 'HOUR' OR duration_minutes = 60);

COMMIT;
