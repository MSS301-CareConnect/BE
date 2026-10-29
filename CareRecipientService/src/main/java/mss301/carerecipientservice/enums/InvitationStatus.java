package mss301.carerecipientservice.enums;

// Lời mời người cùng quản lý hồ sơ (D19): PENDING → ACCEPTED | DECLINED | REVOKED | EXPIRED — đồng bộ CHECK trong SQL.
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    REVOKED,
    EXPIRED
}
