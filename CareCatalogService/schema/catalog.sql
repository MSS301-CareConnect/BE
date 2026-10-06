-- Schema khởi tạo cho CareCatalogService (DB độc lập CARECONNECT_CATALOG).
-- Áp dụng thủ công bằng: psql -U postgres -d CARECONNECT_CATALOG -v ON_ERROR_STOP=1 -f schema/catalog.sql
-- Không startup DDL và không Flyway (ddl-auto: validate).
-- center_id tham chiếu IdentityService / Center domain bằng UUID (không FK xuyên database).

BEGIN;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Danh mục dịch vụ chuẩn của toàn nền tảng (chỉ ADMIN quản lý)
CREATE TABLE care_services (
    care_services_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    category VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_care_services_status ON care_services (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_care_services_deleted ON care_services (deleted_at);

-- 2. Dịch vụ mà trung tâm chăm sóc cung cấp (CENTER_MANAGER quản lý)
CREATE TABLE center_services (
    center_services_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    care_services_id UUID NOT NULL REFERENCES care_services(care_services_id),
    center_id UUID NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(50) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_center_services_center ON center_services (center_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_center_services_care_service ON center_services (care_services_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_center_services_status ON center_services (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_center_services_deleted ON center_services (deleted_at);
-- Một trung tâm chỉ đăng ký một dịch vụ chuẩn một lần (chưa xóa mềm)
CREATE UNIQUE INDEX uk_center_services_center_care ON center_services (center_id, care_services_id) WHERE deleted_at IS NULL;

-- 3. Bảng giá theo đơn vị giờ / ngày / ca của dịch vụ trung tâm (CENTER_MANAGER quản lý)
CREATE TABLE price_plans (
    price_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    center_service_id UUID NOT NULL REFERENCES center_services(center_services_id),
    plan_name VARCHAR(255) NOT NULL,
    pricing_unit VARCHAR(50) NOT NULL CHECK (pricing_unit IN ('HOUR', 'DAY', 'SHIFT')),
    duration_minutes INT NOT NULL CHECK (duration_minutes > 0),
    base_price DECIMAL(10, 2) NOT NULL CHECK (base_price >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_price_plans_center_service ON price_plans (center_service_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_price_plans_is_active ON price_plans (is_active) WHERE deleted_at IS NULL;
CREATE INDEX idx_price_plans_deleted ON price_plans (deleted_at);

COMMIT;
