package mss301.carerecipientservice.enums;

// Quyền của người quản lý trên hồ sơ, xếp tăng dần: VIEWER < EDITOR < OWNER (thứ tự khai báo là thứ tự quyền).
// Đồng bộ CHECK recipient_managers.access_level.
public enum RecipientAccessLevel {
    VIEWER,
    EDITOR,
    OWNER;

    public boolean covers(RecipientAccessLevel requiredLevel) {
        return compareTo(requiredLevel) >= 0;
    }
}
