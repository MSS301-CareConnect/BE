BEGIN;

CREATE TABLE IF NOT EXISTS provider_media(
    provider_media_id uuid PRIMARY KEY,
    owner_type varchar(30) NOT NULL
        CHECK(owner_type IN('CARE_CENTER', 'CAREGIVER_PROFILE','CERTIFICATE')),

    owner_id uuid NOT NULL,
    media_file_id uuid NOT NULL,
    media_file_type varchar(30) NOT NULL
        CHECK( media_file_type IN('IMAGE', 'VIDEO', 'DOCUMENT')),

    display_order integer NOT NULL DEFAULT 0
        CHECK(display_order >= 0),

    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_provider_media_file
    UNIQUE(media_file_id)
);

CREATE INDEX IF NOT EXISTS idx_provider_media_owner
    ON provider_media (owner_type, owner_id);

COMMIT;