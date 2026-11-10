BEGIN;
ALTER TABLE certificates DROP CONSTRAINT certificates_verification_status_check;
UPDATE certificates SET verification_status = 'ADMIN_VERIFIED' WHERE verification_status = 'VERIFIED';
ALTER TABLE certificates ADD CONSTRAINT certificates_verification_status_check CHECK (verification_status IN ('PENDING', 'CENTER_VERIFIED', 'ADMIN_VERIFIED', 'REJECTED'));
ALTER TABLE certificates ADD COLUMN center_verified_by uuid;
ALTER TABLE certificates ADD COLUMN center_verified_at timestamptz;
ALTER TABLE certificates ADD COLUMN center_verification_reason varchar(500);
CREATE TABLE provider_outbox (
    id uuid PRIMARY KEY,
    center_id uuid NOT NULL,
    event_type varchar(30) NOT NULL CHECK (event_type IN ('ACTIVATE', 'SUSPEND', 'RESUME')),
    sequence_no bigint NOT NULL,
    payload jsonb NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts integer NOT NULL,
    next_attempt_at timestamptz NOT NULL,
    last_error varchar(500),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT uk_provider_outbox_event UNIQUE (center_id, event_type, sequence_no)
);
CREATE INDEX idx_provider_outbox_due ON provider_outbox (status, next_attempt_at);
COMMIT;
