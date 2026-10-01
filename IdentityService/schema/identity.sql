-- Initial schema for a NEW, isolated IdentityService database.
-- Apply manually with psql -v ON_ERROR_STOP=1 -f schema/identity.sql.
-- No startup DDL and no Flyway. Existing databases need a reviewed change script.
BEGIN;
CREATE TABLE users (
    user_id uuid PRIMARY KEY,
    email varchar(255) NOT NULL,
    password varchar(255),
    full_name varchar(255) NOT NULL,
    phone_number varchar(255),
    user_status varchar(255) NOT NULL CHECK (user_status IN ('ACTIVE', 'LOCKED')),
    center_id uuid,
    authz_version bigint NOT NULL DEFAULT 0 CHECK (authz_version >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT users_normalized_email CHECK (email = lower(btrim(email)))
);
CREATE INDEX idx_users_center ON users (center_id) WHERE deleted_at IS NULL;
CREATE TABLE roles (
    role_id uuid PRIMARY KEY,
    role_code varchar(255) NOT NULL,
    role_name varchar(255) NOT NULL,
    description varchar(255),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT uk_roles_code UNIQUE (role_code)
);
CREATE TABLE permissions (
    permission_id uuid PRIMARY KEY,
    permission_code varchar(255) NOT NULL UNIQUE,
    permission_name varchar(255) NOT NULL,
    resource varchar(255) NOT NULL,
    description varchar(255),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz
);
CREATE TABLE user_roles (
    user_id uuid NOT NULL REFERENCES users(user_id),
    role_id uuid NOT NULL REFERENCES roles(role_id),
    assigned_by uuid,
    assigned_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_roles_role ON user_roles (role_id);
CREATE TABLE role_permissions (
    role_id uuid NOT NULL REFERENCES roles(role_id),
    permission_id uuid NOT NULL REFERENCES permissions(permission_id),
    granted_at timestamptz NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX idx_role_permissions_permission ON role_permissions (permission_id);
CREATE TABLE identity_audit (
    id uuid PRIMARY KEY,
    actor_id uuid,
    target_id uuid,
    center_id uuid,
    action varchar(255) NOT NULL,
    detail varchar(255) NOT NULL,
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_identity_audit_target ON identity_audit(target_id, created_at);
COMMIT;
