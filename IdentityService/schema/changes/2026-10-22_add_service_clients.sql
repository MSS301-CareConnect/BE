-- IAM-01 (2026-10-22): danh tính máy cho token SERVICE (client-credentials).
-- Áp tay một lần cho DB IdentityService đã có dữ liệu: psql -v ON_ERROR_STOP=1 -f schema/changes/2026-10-22_add_service_clients.sql
-- Dữ liệu seed do ServiceClientInitializer ghi từ env IDENTITY_SERVICE_CLIENTS lúc khởi động (không hardcode secret ở đây).
BEGIN;
CREATE TABLE IF NOT EXISTS service_clients (
    client_uuid uuid PRIMARY KEY,
    client_id varchar(50) NOT NULL,
    secret_hash varchar(255) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    description varchar(255),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz
);
-- client_id duy nhất trong các client chưa xóa mềm (xóa rồi tạo lại cùng tên được).
CREATE UNIQUE INDEX IF NOT EXISTS uk_service_clients_client_id ON service_clients (client_id) WHERE deleted_at IS NULL;
COMMIT;
