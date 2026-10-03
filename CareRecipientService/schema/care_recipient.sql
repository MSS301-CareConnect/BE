-- Initial schema for a NEW, isolated CareRecipientService database.
-- Apply manually with psql -v ON_ERROR_STOP=1 -f schema/care_recipient.sql.
-- No startup DDL and no Flyway. Existing databases need a reviewed change script in schema/changes/.
-- user_id / created_by / updated_by / added_by reference IdentityService users by UUID (no cross-service FK).
BEGIN;
CREATE TABLE care_recipients (
    recipient_id uuid PRIMARY KEY,
    full_name varchar(255) NOT NULL,
    date_of_birth date,
    gender varchar(255) CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    phone_number varchar(255),
    avatar_url varchar(1000),
    address_line varchar(500),
    ward varchar(255),
    district varchar(255),
    province varchar(255),
    latitude numeric(9,6) CHECK (latitude BETWEEN -90 AND 90),
    longitude numeric(9,6) CHECK (longitude BETWEEN -180 AND 180),
    blood_type varchar(255) CHECK (blood_type IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-', 'UNKNOWN')),
    height_cm numeric(5,2) CHECK (height_cm > 0),
    weight_kg numeric(5,2) CHECK (weight_kg > 0),
    medical_history text,
    chronic_conditions text,
    allergies text,
    mobility_level varchar(255) CHECK (mobility_level IN ('INDEPENDENT', 'NEEDS_ASSISTANCE', 'WHEELCHAIR', 'BEDRIDDEN')),
    independence_level varchar(255) CHECK (independence_level IN ('INDEPENDENT', 'PARTIALLY_DEPENDENT', 'FULLY_DEPENDENT')),
    dietary_notes text,
    sleep_habits text,
    behavior_notes text,
    care_requirements text,
    status varchar(255) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    created_by uuid NOT NULL,
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT care_recipients_coordinates_pair CHECK ((latitude IS NULL) = (longitude IS NULL))
);
CREATE INDEX idx_care_recipients_status ON care_recipients (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_care_recipients_full_name ON care_recipients (lower(full_name));
CREATE INDEX idx_care_recipients_created_by ON care_recipients (created_by);

CREATE TABLE emergency_contacts (
    contact_id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL REFERENCES care_recipients(recipient_id),
    full_name varchar(255) NOT NULL,
    relationship varchar(100) NOT NULL,
    phone_number varchar(255) NOT NULL,
    is_primary boolean NOT NULL DEFAULT false,
    priority integer NOT NULL DEFAULT 0 CHECK (priority >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz
);
CREATE INDEX idx_emergency_contacts_recipient ON emergency_contacts (recipient_id) WHERE deleted_at IS NULL;
-- Tối đa một liên hệ chính (chưa xóa mềm) trên mỗi hồ sơ.
CREATE UNIQUE INDEX uk_emergency_contacts_primary ON emergency_contacts (recipient_id) WHERE is_primary AND deleted_at IS NULL;

CREATE TABLE medications (
    medication_id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL REFERENCES care_recipients(recipient_id),
    medication_name varchar(255) NOT NULL,
    dosage varchar(255),
    frequency varchar(255),
    time_of_day jsonb,
    route varchar(255) CHECK (route IN ('ORAL', 'INJECTION', 'TOPICAL', 'INHALATION', 'OTHER')),
    start_date date NOT NULL,
    end_date date,
    prescribed_by varchar(255),
    usage_notes text,
    is_active boolean NOT NULL DEFAULT true,
    updated_by uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT medications_date_range CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT medications_time_of_day_array CHECK (time_of_day IS NULL OR jsonb_typeof(time_of_day) = 'array')
);
CREATE INDEX idx_medications_recipient ON medications (recipient_id) WHERE deleted_at IS NULL;

CREATE TABLE recipient_managers (
    recipient_manager_id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL REFERENCES care_recipients(recipient_id),
    user_id uuid NOT NULL,
    relationship_type varchar(255) NOT NULL CHECK (relationship_type IN ('SELF', 'CHILD', 'SPOUSE', 'PARENT', 'SIBLING', 'GRANDCHILD', 'RELATIVE', 'OTHER')),
    is_primary boolean NOT NULL DEFAULT false,
    access_level varchar(255) NOT NULL CHECK (access_level IN ('OWNER', 'EDITOR', 'VIEWER')),
    added_by uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uk_recipient_managers_user UNIQUE (recipient_id, user_id),
    -- Người quản lý chính luôn là OWNER.
    CONSTRAINT recipient_managers_primary_owner CHECK (NOT is_primary OR access_level = 'OWNER')
);
CREATE INDEX idx_recipient_managers_user ON recipient_managers (user_id);
CREATE INDEX idx_recipient_managers_recipient ON recipient_managers (recipient_id);
-- Tối đa một người quản lý chính; "đúng một" do service đảm bảo (chuyển primary trong một transaction).
CREATE UNIQUE INDEX uk_recipient_managers_primary ON recipient_managers (recipient_id) WHERE is_primary;
COMMIT;
