package mss301.identityservice.enums;

// Giá trị cột identity_audit.action (lưu bằng name()).
public enum AuditAction {
    ACCOUNT_CREATED,
    ADMIN_BOOTSTRAPPED,
    PROFILE_UPDATED,
    STATUS_CHANGED,
    ACCOUNT_SOFT_DELETED,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    SESSIONS_REVOKED,
    ROLE_ASSIGNED,
    ROLE_REVOKED,
    ROLE_CREATED,
    ROLE_PERMISSIONS_CHANGED
}
