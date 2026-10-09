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
    actual_start_at       timestamptz,
    actual_end_at         timestamptz,
    caregiver_id          uuid,
    caregiver_user_id     uuid,
    availability_slot_id  uuid,
    status                varchar(20)  NOT NULL,
    cancel_reason         text,
    note                  text,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    deleted_at            timestamptz,
    version               bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uk_booking_occurrences_no UNIQUE (booking_id, occurrence_no),
    CONSTRAINT ck_booking_occurrences_no CHECK (occurrence_no >= 1),
    CONSTRAINT ck_booking_occurrences_range CHECK (end_at > start_at),
    CONSTRAINT ck_booking_occurrences_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_booking_occurrences_actual_range CHECK (
        actual_start_at IS NULL OR actual_end_at IS NULL OR actual_end_at > actual_start_at),
    CONSTRAINT ck_booking_occurrences_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED',
                                                               'CANCELLED', 'MISSED')),
    CONSTRAINT ck_booking_occurrences_caregiver_pair CHECK ((caregiver_id IS NULL) = (caregiver_user_id IS NULL)),
    CONSTRAINT ck_booking_occurrences_version CHECK (version >= 0)
);

CREATE INDEX idx_booking_occurrences_caregiver ON booking_occurrences (caregiver_id, start_at);
CREATE INDEX idx_booking_occurrences_caregiver_user ON booking_occurrences (caregiver_user_id, start_at);
CREATE INDEX idx_booking_occurrences_booking ON booking_occurrences (booking_id, start_at);
-- Sau này có thể chống trùng ngay ở DB (cần btree_gist), khi điều kiện không còn phụ thuộc trạng thái booking:
--   EXCLUDE USING gist (caregiver_id WITH =, tstzrange(start_at, end_at) WITH &&)
--   WHERE (status IN ('SCHEDULED', 'IN_PROGRESS'))


-- Dịch vụ trong booking. Tên / đơn vị / giá NULL tới khi nối CareCatalogService (không tin giá từ client).
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
    total_price        numeric(14,2),
    created_at         timestamptz   NOT NULL,

    CONSTRAINT uk_booking_details_service UNIQUE (booking_id, care_service_id),
    CONSTRAINT ck_booking_details_unit_type CHECK (unit_type IS NULL OR unit_type IN ('HOUR', 'DAY', 'SHIFT')),
    CONSTRAINT ck_booking_details_quantity CHECK (quantity IS NULL OR quantity > 0),
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

COMMIT;

-- Chưa tạo ở giai đoạn này (thiết kế: docs/2026-10-09-PLAN_BOOKING.md §3.5): occurrence_services, care_reports
-- (occurrence_id UNIQUE, chỉ số sức khỏe có CHECK), feedbacks (booking_id UNIQUE, rating 1–5).
