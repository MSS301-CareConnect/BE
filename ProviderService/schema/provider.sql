-- Initial schema for a NEW ProviderService database.
-- Apply manually only for a new database.
-- No Flyway; Hibernate uses ddl-auto=validate.

BEGIN;

CREATE TABLE care_centers
(
    center_id       uuid PRIMARY KEY,
    center_name     varchar(255) NOT NULL,
    phone_number    varchar(30),
    email           varchar(255) NOT NULL,
    address         text         NOT NULL,
    operating_hours varchar(500),
    center_status   varchar(50)  NOT NULL
        CHECK (center_status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'REJECTED', 'ARCHIVED')),
    status_reason   varchar(500),

    manager_user_id uuid,
    created_by      uuid         NOT NULL,
    updated_by      uuid,
    version         bigint       NOT NULL DEFAULT 0
        CHECK (version >= 0),

    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    deleted_at      timestamptz,

    CONSTRAINT uk_care_centers_email UNIQUE (email),
    CONSTRAINT care_centers_normalized_email
        CHECK (email = lower(btrim(email)))
);

CREATE INDEX idx_care_centers_status
    ON care_centers (center_status) WHERE deleted_at IS NULL;

CREATE INDEX idx_care_centers_name
    ON care_centers (lower(center_name)) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_care_centers_manager_active
    ON care_centers (manager_user_id) WHERE deleted_at IS NULL AND manager_user_id IS NOT NULL;


CREATE TABLE caregiver_profiles
(
    caregiver_id        uuid PRIMARY KEY,
    user_id             uuid          NOT NULL,
    center_id           uuid          NOT NULL
        REFERENCES care_centers (center_id),

    full_name           varchar(255)  NOT NULL,
    gender              varchar(30)
        CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),

    date_of_birth       date,
    experience_years    integer       NOT NULL DEFAULT 0
        CHECK (experience_years >= 0),
    bio                 text,
    rating_average      numeric(3, 2) NOT NULL DEFAULT 0
        CHECK (rating_average BETWEEN 0 AND 5),

    rating_count        integer       NOT NULL DEFAULT 0
        CHECK (rating_count >= 0),

    verification_status varchar(50)   NOT NULL
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),

    caregiver_status    varchar(50)   NOT NULL
        CHECK (caregiver_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED')),

    verification_reason varchar(500),
    verified_by         uuid,
    verified_at         timestamptz,

    created_by          uuid          NOT NULL,
    updated_by          uuid,

    version             bigint        NOT NULL DEFAULT 0
        CHECK (version >= 0),

    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    deleted_at          timestamptz,

    CONSTRAINT uk_caregiver_profiles_user UNIQUE (user_id)
);

CREATE INDEX idx_caregiver_profiles_center
    ON caregiver_profiles (center_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_caregiver_profiles_status
    ON caregiver_profiles (caregiver_status, verification_status) WHERE deleted_at IS NULL;


CREATE TABLE certificates
(
    certificate_id      uuid PRIMARY KEY,
    caregiver_id        uuid         NOT NULL
        REFERENCES caregiver_profiles (caregiver_id),

    certificate_name    varchar(255) NOT NULL,
    issued_by           varchar(255) NOT NULL,
    credential_number   varchar(255),

    issued_date         date,
    expiry_date         date,

    verification_status varchar(50)  NOT NULL
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),

    verification_reason varchar(500),
    verified_by         uuid,
    verified_at         timestamptz,

    version             bigint       NOT NULL DEFAULT 0
        CHECK (version >= 0),

    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    deleted_at          timestamptz,

    CONSTRAINT certificates_date_range
        CHECK (
            expiry_date IS NULL
                OR issued_date IS NULL
                OR expiry_date >= issued_date
            )
);

CREATE INDEX idx_certificates_caregiver ON certificates (caregiver_id) WHERE deleted_at IS NULL;

CREATE TABLE provider_media(
    provider_media_id uuid PRIMARY KEY,
    owner_type        varchar(30) NOT NULL
        CHECK (owner_type IN ('CARE_CENTER', 'CAREGIVER_PROFILE', 'CERTIFICATE')),
    owner_id          uuid        NOT NULL,
    media_file_id     uuid        NOT NULL,
    media_file_type        varchar(30) NOT NULL
        CHECK (media_file_type IN ('IMAGE', 'VIDEO', 'DOCUMENT')),
    display_order     integer     NOT NULL DEFAULT 0
        CHECK (display_order >= 0),
    created_by        uuid        NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_provider_media_file
        UNIQUE (media_file_id)
);

CREATE INDEX idx_provider_media_owner
    ON provider_media (owner_type, owner_id);

CREATE TABLE work_shifts
(
    work_shift_id         uuid PRIMARY KEY,
    center_id             uuid        NOT NULL
        REFERENCES care_centers (center_id),

    work_date             date        NOT NULL,
    start_time            time        NOT NULL,
    end_time              time        NOT NULL,
    shift_type            varchar(50) NOT NULL,
    shift_status          varchar(50) NOT NULL
        CHECK (shift_status IN ('DRAFT', 'OPEN', 'CLOSED', 'CANCELLED', 'COMPLETED')),

    capacity              integer     NOT NULL DEFAULT 1
        CHECK (capacity > 0),

    registration_deadline timestamptz,
    note                  text,
    created_by            uuid        NOT NULL,
    updated_by            uuid,
    version               bigint      NOT NULL DEFAULT 0
        CHECK (version >= 0),

    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    deleted_at            timestamptz,

    CONSTRAINT work_shifts_time_range
        CHECK (end_time > start_time)
);

CREATE INDEX idx_work_shifts_center_date
    ON work_shifts (center_id, work_date) WHERE deleted_at IS NULL;

CREATE INDEX idx_work_shifts_status
    ON work_shifts (shift_status) WHERE deleted_at IS NULL;


CREATE TABLE shift_registrations
(
    registration_id     uuid PRIMARY KEY,
    work_shift_id       uuid        NOT NULL
        REFERENCES work_shifts (work_shift_id),

    caregiver_id        uuid        NOT NULL
        REFERENCES caregiver_profiles (caregiver_id),

    registration_status varchar(50) NOT NULL
        CHECK (registration_status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),

    caregiver_note      text,
    reviewed_by         uuid,
    reviewed_at         timestamptz,
    rejection_reason    varchar(500),

    version             bigint      NOT NULL DEFAULT 0
        CHECK (version >= 0),

    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    deleted_at          timestamptz,

    CONSTRAINT uk_shift_registrations_shift_caregiver
        UNIQUE (work_shift_id, caregiver_id)
);

CREATE INDEX idx_shift_registrations_shift
    ON shift_registrations (work_shift_id, registration_status);

CREATE INDEX idx_shift_registrations_caregiver
    ON shift_registrations (caregiver_id, registration_status);


CREATE TABLE caregiver_schedules
(
    schedule_id         uuid PRIMARY KEY,
    registration_id     uuid        NOT NULL
        REFERENCES shift_registrations (registration_id),

    caregiver_id        uuid        NOT NULL
        REFERENCES caregiver_profiles (caregiver_id),

    work_shift_id       uuid        NOT NULL
        REFERENCES work_shifts (work_shift_id),

    schedule_status     varchar(50) NOT NULL
        CHECK (schedule_status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),

    assigned_by         uuid        NOT NULL,
    assigned_at         timestamptz NOT NULL,
    note                text,
    cancellation_reason varchar(500),
    cancelled_at        timestamptz,

    version             bigint      NOT NULL DEFAULT 0
        CHECK (version >= 0),

    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    deleted_at          timestamptz,

    CONSTRAINT uk_caregiver_schedules_registration
        UNIQUE (registration_id),

    CONSTRAINT uk_caregiver_schedules_shift_caregiver
        UNIQUE (work_shift_id, caregiver_id)
);

CREATE INDEX idx_caregiver_schedules_caregiver
    ON caregiver_schedules (caregiver_id, schedule_status);

CREATE INDEX idx_caregiver_schedules_shift
    ON caregiver_schedules (work_shift_id);

COMMIT;
