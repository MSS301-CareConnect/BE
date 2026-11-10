-- Đợt 4 (2026-10-30, D20 / H-09): khiếu nại WITHDRAWN + một khiếu nại đang mở mỗi buổi.
-- Tên CHECK do PostgreSQL tự sinh (cột status/action/from_status/to_status) — kiểm lại bằng \d complaints trước khi chạy.
BEGIN;
ALTER TABLE complaints DROP CONSTRAINT IF EXISTS complaints_status_check;
ALTER TABLE complaints ADD CONSTRAINT complaints_status_check
    CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','ESCALATED','RESOLVED_BY_ADMIN','WITHDRAWN'));
ALTER TABLE complaint_events DROP CONSTRAINT IF EXISTS complaint_events_action_check;
ALTER TABLE complaint_events ADD CONSTRAINT complaint_events_action_check
    CHECK (action IN ('CREATED','ACCEPT','RESOLVE','ESCALATE','ADMIN_RESOLVE','WITHDRAW'));
ALTER TABLE complaint_events DROP CONSTRAINT IF EXISTS complaint_events_from_status_check;
ALTER TABLE complaint_events ADD CONSTRAINT complaint_events_from_status_check
    CHECK (from_status IN ('OPEN','IN_PROGRESS','RESOLVED','ESCALATED','RESOLVED_BY_ADMIN','WITHDRAWN'));
ALTER TABLE complaint_events DROP CONSTRAINT IF EXISTS complaint_events_to_status_check;
ALTER TABLE complaint_events ADD CONSTRAINT complaint_events_to_status_check
    CHECK (to_status IN ('OPEN','IN_PROGRESS','RESOLVED','ESCALATED','RESOLVED_BY_ADMIN','WITHDRAWN'));
CREATE UNIQUE INDEX IF NOT EXISTS uq_complaints_open_occurrence ON complaints (occurrence_id)
    WHERE occurrence_id IS NOT NULL AND deleted_at IS NULL AND status IN ('OPEN','IN_PROGRESS','ESCALATED');
COMMIT;
