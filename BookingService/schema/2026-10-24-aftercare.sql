BEGIN;
-- Aftercare (Người C), 2026-10-24
CREATE TABLE notifications (
    notification_id uuid PRIMARY KEY,
    recipient_user_id uuid NOT NULL,
    type varchar(40) NOT NULL CHECK (type IN ('BOOKING_CREATED','BOOKING_ACCEPTED','BOOKING_REJECTED','BOOKING_CONFIRMED','BOOKING_CANCELLED','OCCURRENCE_CANCELLED','CAREGIVER_ASSIGNED','CAREGIVER_UNASSIGNED','SESSION_STARTED','SESSION_ENDED','REPORT_SUBMITTED','OCCURRENCE_MISSED','NO_SHOW_REPORTED','BOOKING_COMPLETED','PAYMENT_SUCCEEDED','REFUND_COMPLETED','REMAINING_DUE','REMAINING_OVERDUE','COMPLAINT_UPDATED','REVIEW_CREATED','REVIEW_REPLIED','SETTLEMENT_UPDATED','CENTER_ACTIVATED','SHIFT_REMINDER')),
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
    status varchar(30) NOT NULL CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','ESCALATED','RESOLVED_BY_ADMIN')),
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
    action varchar(30) NOT NULL CHECK (action IN ('CREATED','ACCEPT','RESOLVE','ESCALATE','ADMIN_RESOLVE')),
    from_status varchar(30) CHECK (from_status IN ('OPEN','IN_PROGRESS','RESOLVED','ESCALATED','RESOLVED_BY_ADMIN')),
    to_status varchar(30) NOT NULL CHECK (to_status IN ('OPEN','IN_PROGRESS','RESOLVED','ESCALATED','RESOLVED_BY_ADMIN')),
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
CREATE INDEX idx_complaint_events ON complaint_events (complaint_id, created_at);
ALTER TABLE complaints ADD CONSTRAINT fk_complaint_booking FOREIGN KEY (booking_id) REFERENCES bookings(booking_id);
ALTER TABLE complaints ADD CONSTRAINT fk_complaint_occurrence FOREIGN KEY (occurrence_id) REFERENCES booking_occurrences(occurrence_id);
ALTER TABLE complaint_events ADD CONSTRAINT fk_complaint_event FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id);
ALTER TABLE booking_occurrences ADD COLUMN reminder_sent_at timestamptz;
ALTER TABLE booking_outbox DROP CONSTRAINT ck_booking_outbox_event_type;
ALTER TABLE booking_outbox ADD CONSTRAINT ck_booking_outbox_event_type CHECK (event_type IN ('REMAINING_INVOICE', 'REFUND', 'RATING', 'COMPLAINT_REFUND'));
COMMIT;
