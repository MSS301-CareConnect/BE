package mss301.carerecipientservice.enums;

// Trạng thái hồ sơ; ARCHIVED chỉ do DELETE (kèm deleted_at) — đồng bộ CHECK care_recipients.status.
public enum RecipientStatus {
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
