-- Schema khởi tạo cho BookingService (DB độc lập CARECONNECT_BOOKING).
-- Áp dụng thủ công MỘT LẦN cho DB mới:
--   Get-Content -Raw schema/booking.sql | docker compose exec -T postgres psql -U <user> -d CARECONNECT_BOOKING -v ON_ERROR_STOP=1
-- Không Flyway, không startup DDL (ddl-auto: validate). DB đã có dữ liệu → change script ở schema/changes/.
-- Tham chiếu service khác bằng UUID, không FK xuyên DB:
--   customer_id, caregiver_user_id, changed_by… = users.user_id (IdentityService)
--   recipient_id = care_recipients (CareRecipientService)
--   center_id, caregiver_id, availability_slot_id = care_centers / caregiver_profiles / caregiver_schedules (ProviderService)
--   care_service_id, center_service_id, price_plan_id = CareCatalogService
-- Thời điểm: timestamptz (UTC); ngày/giờ theo lịch: date/time theo bookings.timezone.

BEGIN;

-- BK-09: exclusion constraint chống trùng lịch caregiver cần toán tử = của btree trên gist.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE bookings (
    booking_id          uuid PRIMARY KEY,
    booking_code        varchar(20)   NOT NULL,
    customer_id         uuid          NOT NULL,
    recipient_id        uuid          NOT NULL,
    center_id           uuid          NOT NULL,
    caregiver_id        uuid,
    caregiver_user_id   uuid,
    schedule_type       varchar(20)   NOT NULL,
    start_date          date          NOT NULL,
    end_date            date          NOT NULL,
    start_time          time          NOT NULL,
    end_time            time          NOT NULL,
    days_of_week        smallint[],
    timezone            varchar(64)   NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    recipient_snapshot  jsonb         NOT NULL,
    service_address     text          NOT NULL,
    customer_note       text,
    status              varchar(30)   NOT NULL,
    subtotal            numeric(14,2),
    deposit_amount      numeric(14,2),
    total_amount        numeric(14,2),
    acceptance_due_at   timestamptz,
    accepted_by         uuid,
    accepted_at         timestamptz,
    deposit_due_at      timestamptz,
    confirmed_at        timestamptz,
    started_at          timestamptz,
    completed_at        timestamptz,
    cancelled_at        timestamptz,
    cancelled_by        uuid,
    cancel_reason       text,
    rejected_by         uuid,
    rejected_at         timestamptz,
    rejected_reason     text,
    idempotency_key     varchar(100),
    idempotency_hash    varchar(64),
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    deleted_at          timestamptz,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uk_bookings_code UNIQUE (booking_code),
    CONSTRAINT ck_bookings_schedule_type CHECK (schedule_type IN ('ONE_TIME', 'DAILY', 'WEEKLY')),
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING', 'AWAITING_DEPOSIT', 'CONFIRMED', 'IN_PROGRESS',
                                                    'COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_bookings_date_range CHECK (end_date >= start_date),
    CONSTRAINT ck_bookings_one_time_single_day CHECK (schedule_type <> 'ONE_TIME' OR end_date = start_date),
    -- Chưa hỗ trợ ca qua đêm (TODO): giờ kết thúc phải sau giờ bắt đầu trong cùng ngày.
    CONSTRAINT ck_bookings_time_range CHECK (end_time > start_time),
    -- WEEKLY: 1..7 thứ, mỗi giá trị trong [1, 7]; lịch khác không có days_of_week.
    CONSTRAINT ck_bookings_days_of_week CHECK (
        (schedule_type = 'WEEKLY'
            AND days_of_week IS NOT NULL
            AND cardinality(days_of_week) BETWEEN 1 AND 7
            AND days_of_week <@ ARRAY[1, 2, 3, 4, 5, 6, 7]::smallint[])
        OR (schedule_type <> 'WEEKLY' AND days_of_week IS NULL)),
    -- Phân công luôn ghi cả hai id caregiver (Provider + Identity) cùng lúc.
    CONSTRAINT ck_bookings_caregiver_pair CHECK ((caregiver_id IS NULL) = (caregiver_user_id IS NULL)),
    CONSTRAINT ck_bookings_amounts CHECK ((subtotal IS NULL OR subtotal >= 0)
        AND (deposit_amount IS NULL OR deposit_amount >= 0)
        AND (total_amount IS NULL OR total_amount >= 0)),
    CONSTRAINT ck_bookings_version CHECK (version >= 0)
);

CREATE INDEX idx_bookings_customer ON bookings (customer_id, created_at DESC);
CREATE INDEX idx_bookings_recipient ON bookings (recipient_id);
CREATE INDEX idx_bookings_center ON bookings (center_id, status);
CREATE INDEX idx_bookings_caregiver ON bookings (caregiver_id);
CREATE INDEX idx_bookings_caregiver_user ON bookings (caregiver_user_id);
CREATE INDEX idx_bookings_status ON bookings (status);
-- Scheduler quét PENDING quá hạn duyệt / AWAITING_DEPOSIT quá hạn cọc.
CREATE INDEX idx_bookings_acceptance_due ON bookings (acceptance_due_at) WHERE status = 'PENDING';
CREATE INDEX idx_bookings_deposit_due ON bookings (deposit_due_at) WHERE status = 'AWAITING_DEPOSIT';
-- Header Idempotency-Key: một key chỉ tạo một booking cho mỗi khách hàng.
CREATE UNIQUE INDEX uk_bookings_idempotency ON bookings (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;


-- Mỗi dòng là một buổi chăm sóc (không có bảng care_sessions). Care report (giai đoạn sau) tham chiếu occurrence_id.
CREATE TABLE booking_occurrences (
    occurrence_id         uuid PRIMARY KEY,
    booking_id            uuid         NOT NULL REFERENCES bookings (booking_id),
    occurrence_no         integer      NOT NULL,
    start_at              timestamptz  NOT NULL,
    end_at                timestamptz  NOT NULL,
    duration_minutes      integer      NOT NULL,
    -- Giá buổi lúc đặt (BK-01); NULL với booking tạo trước khi có giá từng buổi.
    price_amount          numeric(14,2),
    acknowledged_at       timestamptz,
    actual_start_at       timestamptz,
    actual_end_at         timestamptz,
    caregiver_id          uuid,
    caregiver_user_id     uuid,
    availability_slot_id  uuid,
    status                varchar(20)  NOT NULL,
    cancel_reason         text,
    note                  text,
    -- OPS-06: buổi MISSED do ai; caregiver báo khách vắng mặt + quyết định của manager; mốc đã nhắc manager.
    missed_reason         varchar(30),
    no_show_reported_at   timestamptz,
    no_show_reported_by   uuid,
    no_show_note          text,
    no_show_decided_by    uuid,
    no_show_decided_at    timestamptz,
    no_show_reminded_at   timestamptz,
    -- BK-03 / BK-04: buổi bị hủy lúc nào, bởi bên nào; caregiver bị khóa → manager phải đổi người.
    cancelled_at          timestamptz,
    cancelled_by_party    varchar(20),
    needs_reassignment    boolean      NOT NULL DEFAULT false,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    deleted_at            timestamptz,
    version               bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uk_booking_occurrences_no UNIQUE (booking_id, occurrence_no),
    CONSTRAINT ck_booking_occurrences_no CHECK (occurrence_no >= 1),
    CONSTRAINT ck_booking_occurrences_range CHECK (end_at > start_at),
    CONSTRAINT ck_booking_occurrences_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_booking_occurrences_price CHECK (price_amount IS NULL OR price_amount >= 0),
    CONSTRAINT ck_booking_occurrences_actual_range CHECK (
        actual_start_at IS NULL OR actual_end_at IS NULL OR actual_end_at > actual_start_at),
    CONSTRAINT ck_booking_occurrences_status CHECK (status IN ('SCHEDULED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'COMPLETED',
                                                               'CANCELLED', 'MISSED', 'CUSTOMER_NO_SHOW')),
    CONSTRAINT ck_booking_occurrences_caregiver_pair CHECK ((caregiver_id IS NULL) = (caregiver_user_id IS NULL)),
    CONSTRAINT ck_booking_occurrences_missed_reason CHECK (missed_reason IS NULL OR missed_reason IN ('CAREGIVER')),
    CONSTRAINT ck_booking_occurrences_no_show_pair CHECK ((no_show_reported_at IS NULL) = (no_show_reported_by IS NULL)),
    CONSTRAINT ck_booking_occurrences_cancelled_by_party CHECK (
        cancelled_by_party IS NULL OR cancelled_by_party IN ('CUSTOMER', 'CENTER', 'SYSTEM', 'ADMIN')),
    CONSTRAINT ck_booking_occurrences_version CHECK (version >= 0),
    -- BK-09: chốt chặn cuối chống trùng lịch caregiver (khoảng nửa mở). Chỉ theo trạng thái buổi: booking rời trạng thái
    -- giữ chỗ luôn kéo mọi buổi chưa kết thúc về CANCELLED (BookingLifecycleService) nên không cần cột active_slot.
    CONSTRAINT ex_booking_occurrences_caregiver_slot EXCLUDE USING gist (
        caregiver_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
        WHERE (status IN ('SCHEDULED', 'ACKNOWLEDGED', 'IN_PROGRESS'))
);

CREATE INDEX idx_booking_occurrences_caregiver ON booking_occurrences (caregiver_id, start_at);
CREATE INDEX idx_booking_occurrences_caregiver_user ON booking_occurrences (caregiver_user_id, start_at);
CREATE INDEX idx_booking_occurrences_booking ON booking_occurrences (booking_id, start_at);
-- Job MISSED / nhắc quyết định báo vắng (OPS-06) chỉ quét buổi chưa bắt đầu.
CREATE INDEX idx_booking_occurrences_not_started ON booking_occurrences (start_at)
    WHERE status IN ('SCHEDULED', 'ACKNOWLEDGED');


-- Dịch vụ trong booking: giá gốc từ CareCatalogService lúc đặt (không tin giá từ client). Booking cũ có thể NULL giá.
CREATE TABLE booking_details (
    booking_detail_id  uuid PRIMARY KEY,
    booking_id         uuid          NOT NULL REFERENCES bookings (booking_id),
    care_service_id    uuid          NOT NULL,
    center_service_id  uuid,
    price_plan_id      uuid,
    service_name       varchar(255),
    unit_type          varchar(20),
    unit_price         numeric(14,2),
    quantity           integer,
    units_per_occurrence integer,
    total_price        numeric(14,2),
    created_at         timestamptz   NOT NULL,

    CONSTRAINT uk_booking_details_service UNIQUE (booking_id, care_service_id),
    CONSTRAINT ck_booking_details_unit_type CHECK (unit_type IS NULL OR unit_type IN ('HOUR', 'DAY', 'SHIFT')),
    CONSTRAINT ck_booking_details_quantity CHECK (quantity IS NULL OR quantity > 0),
    CONSTRAINT ck_booking_details_units CHECK (units_per_occurrence IS NULL OR units_per_occurrence > 0),
    CONSTRAINT ck_booking_details_prices CHECK ((unit_price IS NULL OR unit_price >= 0)
        AND (total_price IS NULL OR total_price >= 0))
);

CREATE INDEX idx_booking_details_booking ON booking_details (booking_id);


-- Audit chuyển trạng thái (chỉ ghi). changed_by NULL = hệ thống / service nội bộ.
CREATE TABLE booking_status_history (
    history_id   uuid PRIMARY KEY,
    booking_id   uuid          NOT NULL REFERENCES bookings (booking_id),
    from_status  varchar(30),
    to_status    varchar(30)   NOT NULL,
    changed_by   uuid,
    reason       text,
    changed_at   timestamptz   NOT NULL,

    CONSTRAINT ck_booking_status_history_from CHECK (from_status IS NULL OR from_status IN (
        'PENDING', 'AWAITING_DEPOSIT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_booking_status_history_to CHECK (to_status IN (
        'PENDING', 'AWAITING_DEPOSIT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX idx_booking_status_history_booking ON booking_status_history (booking_id, changed_at);

-- Sự kiện mức buổi (nhận ca, đổi trạng thái buổi, đổi caregiver) — chỉ ghi thêm (OPS-01; API lịch sử ở BK-05).
CREATE TABLE booking_events (
    event_id          uuid PRIMARY KEY,
    booking_id        uuid         NOT NULL REFERENCES bookings (booking_id),
    occurrence_id     uuid         REFERENCES booking_occurrences (occurrence_id),
    event_type        varchar(40)  NOT NULL,
    old_caregiver_id  uuid,
    new_caregiver_id  uuid,
    from_status       varchar(30),
    to_status         varchar(30),
    changed_by        uuid,
    reason            text,
    created_at        timestamptz  NOT NULL,

    CONSTRAINT ck_booking_events_type CHECK (event_type IN ('OCCURRENCE_ACKNOWLEDGED', 'OCCURRENCE_STATUS_CHANGED',
                                                            'CAREGIVER_CHANGED', 'NO_SHOW_REPORTED'))
);

CREATE INDEX idx_booking_events_booking ON booking_events (booking_id, created_at);

-- Ca chăm sóc thực tế của một buổi (OPS-02 / OPS-04). UNIQUE occurrence_id chặn hai lần check-in song song.
CREATE TABLE care_sessions (
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
    auto_closed          boolean       NOT NULL DEFAULT false,
    created_at           timestamptz   NOT NULL,
    updated_at           timestamptz   NOT NULL,
    deleted_at           timestamptz,
    version              bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uk_care_sessions_occurrence UNIQUE (occurrence_id),
    CONSTRAINT ck_care_sessions_status CHECK (status IN ('IN_PROGRESS', 'ENDED')),
    CONSTRAINT ck_care_sessions_checkout CHECK (check_out_at IS NULL OR check_out_at >= check_in_at),
    CONSTRAINT ck_care_sessions_version CHECK (version >= 0)
);

CREATE INDEX idx_care_sessions_booking ON care_sessions (booking_id);
CREATE INDEX idx_care_sessions_open ON care_sessions (status) WHERE status = 'IN_PROGRESS';

-- Checklist của ca: sinh lúc check-in từ dịch vụ của booking + thuốc trong snapshot (OPS-02); OPS-03 cập nhật.
CREATE TABLE care_tasks (
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

CREATE INDEX idx_care_tasks_session ON care_tasks (session_id, sort_order);

-- Transactional outbox: việc gửi sang service khác (hóa đơn REMAINING, hoàn tiền…), retry khi bên nhận lỗi (OPS-04).
CREATE TABLE booking_outbox (
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
    CONSTRAINT ck_booking_outbox_event_type CHECK (event_type IN ('REMAINING_INVOICE', 'REFUND', 'RATING', 'COMPLAINT_REFUND')),
    CONSTRAINT ck_booking_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_booking_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_booking_outbox_due ON booking_outbox (status, next_attempt_at);

COMMIT;

-- Chưa tạo ở giai đoạn này (thiết kế: docs/2026-10-09-PLAN_BOOKING.md §3.5): occurrence_services, care_reports
-- (occurrence_id UNIQUE, chỉ số sức khỏe có CHECK), feedbacks (booking_id UNIQUE, rating 1–5).

-- Aftercare (Người C), 2026-10-24
CREATE TABLE notifications (
    notification_id uuid PRIMARY KEY,
    recipient_user_id uuid NOT NULL,
    type varchar(40) NOT NULL CHECK (type IN ('BOOKING_CREATED','BOOKING_ACCEPTED','BOOKING_REJECTED','BOOKING_CONFIRMED','BOOKING_CANCELLED','OCCURRENCE_CANCELLED','CAREGIVER_ASSIGNED','CAREGIVER_UNASSIGNED','SESSION_STARTED','SESSION_ENDED','REPORT_SUBMITTED','OCCURRENCE_MISSED','NO_SHOW_REPORTED','BOOKING_COMPLETED','PAYMENT_SUCCEEDED','REFUND_COMPLETED','REMAINING_DUE','REMAINING_OVERDUE','COMPLAINT_UPDATED','REVIEW_CREATED','REVIEW_REPLIED','SETTLEMENT_UPDATED','CENTER_ACTIVATED','SHIFT_REMINDER','RECIPIENT_INVITATION','BOOKING_EXPIRED','OUTBOX_FAILED','NO_SHOW_AUTO_ACCEPTED','SESSION_AUTO_CLOSED')),
    title varchar(200) NOT NULL,
    body varchar(1000) NOT NULL,
    ref_type varchar(80),
    ref_id uuid,
    read_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz
);

CREATE TABLE care_session_notes (
    note_id uuid PRIMARY KEY,
    session_id uuid REFERENCES care_sessions(session_id) NOT NULL,
    content varchar(2000) NOT NULL,
    vitals jsonb,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz
);

CREATE TABLE care_reports (
    report_id uuid PRIMARY KEY,
    session_id uuid REFERENCES care_sessions(session_id) NOT NULL UNIQUE,
    occurrence_id uuid NOT NULL,
    booking_id uuid NOT NULL,
    caregiver_user_id uuid NOT NULL,
    overall_condition varchar(30) NOT NULL CHECK (overall_condition IN ('STABLE','IMPROVED','DECLINED','NEEDS_ATTENTION')),
    summary varchar(4000) NOT NULL,
    meal_notes varchar(2000),
    medication_notes varchar(2000),
    mobility_notes varchar(2000),
    mood_notes varchar(2000),
    recommendations varchar(2000),
    status varchar(20) NOT NULL CHECK (status IN ('DRAFT','SUBMITTED')),
    submitted_at timestamptz,
    version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz
);

CREATE TABLE feedbacks (
    feedback_id uuid PRIMARY KEY,
    booking_id uuid REFERENCES bookings(booking_id) NOT NULL,
    caregiver_id uuid NOT NULL,
    caregiver_user_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    center_id uuid NOT NULL,
    customer_display_name varchar(255) NOT NULL,
    rating smallint NOT NULL,
    comment varchar(2000),
    reply varchar(1000),
    replied_by uuid,
    replied_at timestamptz,
    visibility varchar(20) NOT NULL CHECK (visibility IN ('VISIBLE','HIDDEN')),
    hidden_reason varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT uk_feedback_booking_caregiver UNIQUE (booking_id, caregiver_id),
    CONSTRAINT ck_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE complaints (
    complaint_id uuid PRIMARY KEY,
    complaint_code varchar(30) NOT NULL UNIQUE,
    booking_id uuid NOT NULL,
    occurrence_id uuid,
    customer_id uuid NOT NULL,
    center_id uuid NOT NULL,
    caregiver_id uuid,
    target_type varchar(30) NOT NULL CHECK (target_type IN ('CAREGIVER_SERVICE','CENTER')),
    category varchar(30) NOT NULL CHECK (category IN ('QUALITY','ATTITUDE','LATE','SAFETY','BILLING','OTHER')),
    title varchar(200) NOT NULL,
    content varchar(4000) NOT NULL,
    status varchar(30) NOT NULL CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','ESCALATED','RESOLVED_BY_ADMIN','WITHDRAWN')),
    level varchar(20) NOT NULL CHECK (level IN ('CENTER','ADMIN')),
    resolution varchar(4000),
    resolved_by uuid,
    resolved_at timestamptz,
    escalated_at timestamptz,
    escalate_reason varchar(4000),
    refund_amount numeric(14,2),
    version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz
);

CREATE TABLE complaint_events (
    event_id uuid PRIMARY KEY,
    complaint_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    action varchar(30) NOT NULL CHECK (action IN ('CREATED','ACCEPT','RESOLVE','ESCALATE','ADMIN_RESOLVE','WITHDRAW')),
    from_status varchar(30) CHECK (from_status IN ('OPEN','IN_PROGRESS','RESOLVED','ESCALATED','RESOLVED_BY_ADMIN','WITHDRAWN')),
    to_status varchar(30) NOT NULL CHECK (to_status IN ('OPEN','IN_PROGRESS','RESOLVED','ESCALATED','RESOLVED_BY_ADMIN','WITHDRAWN')),
    note varchar(4000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz
);

CREATE TABLE care_report_media (
    report_id uuid NOT NULL REFERENCES care_reports(report_id),
    media_file_id uuid NOT NULL,
    display_order integer NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (report_id, media_file_id)
);
CREATE INDEX idx_notifications_recipient_unread ON notifications (recipient_user_id, read_at, created_at DESC);
CREATE INDEX idx_notes_session ON care_session_notes (session_id, created_at DESC);
CREATE INDEX idx_reports_booking ON care_reports (booking_id, created_at DESC);
CREATE INDEX idx_feedbacks_caregiver ON feedbacks (caregiver_id, created_at DESC);
CREATE INDEX idx_feedbacks_center ON feedbacks (center_id);
CREATE INDEX idx_complaints_scope ON complaints (center_id, customer_id, status);
-- D20: mỗi buổi tối đa một khiếu nại đang mở (ứng dụng kiểm dưới khóa booking; index chặn lần cuối).
CREATE UNIQUE INDEX uq_complaints_open_occurrence ON complaints (occurrence_id)
    WHERE occurrence_id IS NOT NULL AND deleted_at IS NULL AND status IN ('OPEN','IN_PROGRESS','ESCALATED');
CREATE INDEX idx_complaint_events ON complaint_events (complaint_id, created_at);
ALTER TABLE complaints ADD CONSTRAINT fk_complaint_booking FOREIGN KEY (booking_id) REFERENCES bookings(booking_id);
ALTER TABLE complaints ADD CONSTRAINT fk_complaint_occurrence FOREIGN KEY (occurrence_id) REFERENCES booking_occurrences(occurrence_id);
ALTER TABLE complaint_events ADD CONSTRAINT fk_complaint_event FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id);
ALTER TABLE booking_occurrences ADD COLUMN reminder_sent_at timestamptz;
