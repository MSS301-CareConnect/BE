CREATE TABLE IF NOT EXISTS media_files (
    media_file_id uuid PRIMARY KEY,
    bucket_name varchar(100) NOT NULL,
    object_key varchar(1024) NOT NULL,
    original_file_name varchar(255) NOT NULL,
    content_type varchar(100) NOT NULL,
    file_type varchar(20) NOT NULL,
    file_size bigint NOT NULL,
    media_purpose varchar(50) NOT NULL,
    media_status varchar(30) NOT NULL,
    uploaded_by uuid,
    source_service varchar(50) NOT NULL,
    reference_type varchar(80),
    reference_id uuid,
    etag varchar(255),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,

    CONSTRAINT uk_media_files_object_key UNIQUE (object_key),
    CONSTRAINT ck_media_files_size CHECK (file_size > 0),
    CONSTRAINT ck_media_files_type CHECK (file_type IN ('IMAGE', 'VIDEO', 'DOCUMENT')),
    CONSTRAINT ck_media_files_status CHECK (media_status IN ('ACTIVE', 'DELETE_PENDING', 'DELETED')),
    CONSTRAINT ck_media_files_purpose CHECK (media_purpose IN (
        'USER_AVATAR',
        'CARE_CENTER_LOGO',
        'CAREGIVER_AVATAR',
        'CAREGIVER_CERTIFICATE',
        'CARE_RECIPIENT_AVATAR',
        'CARE_RECIPIENT_DOCUMENT',
        'CARE_REPORT_IMAGE',
        'CARE_REPORT_VIDEO',
        'INCIDENT_EVIDENCE',
        'FEEDBACK_ATTACHMENT',
        'CHAT_ATTACHMENT'
    ))
);

CREATE INDEX IF NOT EXISTS idx_media_files_uploaded_by
    ON media_files (uploaded_by)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_media_files_reference
    ON media_files (source_service, reference_type, reference_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_media_files_status
    ON media_files (media_status)
    WHERE deleted_at IS NULL;
