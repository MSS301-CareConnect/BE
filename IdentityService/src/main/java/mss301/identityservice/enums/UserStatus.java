package mss301.identityservice.enums;

// Đồng bộ với CHECK constraint users.user_status trong schema/identity.sql.
public enum UserStatus {
    ACTIVE,
    LOCKED
}
