-- Đợt 2 (2026-10-30): lời mời người cùng quản lý hồ sơ (C-04). Additive, chạy lại được.
BEGIN;

-- Lời mời quản lý hồ sơ (C-04 / D19): người được mời phải chấp nhận; hết hạn sau 7 ngày (tính lười ở service).
CREATE TABLE IF NOT EXISTS recipient_manager_invitations (
    invitation_id uuid PRIMARY KEY,
    recipient_id uuid NOT NULL REFERENCES care_recipients(recipient_id),
    email varchar(255) NOT NULL,
    invitee_user_id uuid,
    relationship_type varchar(255) NOT NULL CHECK (relationship_type IN ('SELF', 'CHILD', 'SPOUSE', 'PARENT', 'SIBLING', 'GRANDCHILD', 'RELATIVE', 'OTHER')),
    access_level varchar(255) NOT NULL CHECK (access_level IN ('OWNER', 'EDITOR', 'VIEWER')),
    status varchar(20) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    invited_by uuid NOT NULL,
    expires_at timestamptz NOT NULL,
    responded_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_recipient_invitations_recipient ON recipient_manager_invitations (recipient_id);
CREATE INDEX IF NOT EXISTS idx_recipient_invitations_invitee ON recipient_manager_invitations (invitee_user_id, status);
-- Tối đa một lời mời đang chờ cho mỗi (hồ sơ, email).
CREATE UNIQUE INDEX IF NOT EXISTS uk_recipient_invitations_pending ON recipient_manager_invitations (recipient_id, email) WHERE status = 'PENDING';

COMMIT;
