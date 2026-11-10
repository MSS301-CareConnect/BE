-- OPS-02 / OPS-04 (2026-10-22): ca chăm sóc + checklist, mốc bắt đầu / hoàn tất booking, transactional outbox.
-- Áp tay một lần cho DB BookingService đã có dữ liệu (sau 2026-10-22_occurrence_acknowledge.sql):
--   psql -v ON_ERROR_STOP=1 -f schema/changes/2026-10-22_care_sessions_outbox.sql
BEGIN;

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS started_at timestamptz;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS completed_at timestamptz;

-- Ca chăm sóc thực tế của một buổi (OPS-02 / OPS-04). UNIQUE occurrence_id chặn hai lần check-in song song.
CREATE TABLE IF NOT EXISTS care_sessions (
    session_id           uuid PRIMARY KEY,
    occurrence_id        uuid          NOT NULL REFERENCES booking_occurrences (occurrence_id),
    booking_id           uuid          NOT NULL REFERENCES bookings (booking_id),
    caregiver_id         uuid          NOT NULL,
    caregiver_user_id    uuid          NOT NULL,
    status               varchar(20)   NOT NULL,
    check_in_at          timestamptz   NOT NULL,
    check_out_at         timestamptz,
    check_in_lat         numeric(9,6),
    check_in_lng         numeric(9,6),
    check_out_lat        numeric(9,6),
    check_out_lng        numeric(9,6),
    ended_by             uuid,
    end_reason           text,
    overdue_reminded_at  timestamptz,
    created_at           timestamptz   NOT NULL,
    updated_at           timestamptz   NOT NULL,
    deleted_at           timestamptz,
    version              bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uk_care_sessions_occurrence UNIQUE (occurrence_id),
    CONSTRAINT ck_care_sessions_status CHECK (status IN ('IN_PROGRESS', 'ENDED')),
    CONSTRAINT ck_care_sessions_checkout CHECK (check_out_at IS NULL OR check_out_at >= check_in_at),
    CONSTRAINT ck_care_sessions_version CHECK (version >= 0)
);

CREATE INDEX IF NOT EXISTS idx_care_sessions_booking ON care_sessions (booking_id);
CREATE INDEX IF NOT EXISTS idx_care_sessions_open ON care_sessions (status) WHERE status = 'IN_PROGRESS';

-- Checklist của ca: sinh lúc check-in từ dịch vụ của booking + thuốc trong snapshot (OPS-02); OPS-03 cập nhật.
CREATE TABLE IF NOT EXISTS care_tasks (
    task_id            uuid PRIMARY KEY,
    session_id         uuid          NOT NULL REFERENCES care_sessions (session_id),
    booking_detail_id  uuid          REFERENCES booking_details (booking_detail_id),
    care_service_id    uuid,
    task_type          varchar(20)   NOT NULL,
    title              varchar(500)  NOT NULL,
    scheduled_time     time,
    status             varchar(20)   NOT NULL,
    skip_reason        text,
    note               text,
    completed_at       timestamptz,
    sort_order         integer       NOT NULL,
    updated_by         uuid,
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    deleted_at         timestamptz,

    CONSTRAINT ck_care_tasks_type CHECK (task_type IN ('SERVICE', 'MEDICATION', 'CUSTOM')),
    CONSTRAINT ck_care_tasks_status CHECK (status IN ('PENDING', 'DONE', 'SKIPPED'))
);

CREATE INDEX IF NOT EXISTS idx_care_tasks_session ON care_tasks (session_id, sort_order);

-- Transactional outbox: việc gửi sang service khác (hóa đơn REMAINING, hoàn tiền…), retry khi bên nhận lỗi (OPS-04).
CREATE TABLE IF NOT EXISTS booking_outbox (
    outbox_id        uuid PRIMARY KEY,
    event_type       varchar(40)   NOT NULL,
    aggregate_id     uuid          NOT NULL,
    -- Khóa khử trùng gửi kèm sang bên nhận; UNIQUE để cùng một việc (vd hoàn tiền một buổi) không bị xếp hai lần.
    idempotency_key  varchar(200)  NOT NULL,
    payload          jsonb         NOT NULL,
    status           varchar(20)   NOT NULL,
    attempts         integer       NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz   NOT NULL,
    last_error       varchar(500),
    sent_at          timestamptz,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    deleted_at       timestamptz,

    CONSTRAINT uk_booking_outbox_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_booking_outbox_event_type CHECK (event_type IN ('REMAINING_INVOICE', 'REFUND')),
    CONSTRAINT ck_booking_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_booking_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX IF NOT EXISTS idx_booking_outbox_due ON booking_outbox (status, next_attempt_at);

COMMIT;
