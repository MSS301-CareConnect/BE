package mss301.aiservice.enums;

// Cảnh báo của một lần kiểm tra chứng chỉ — FE dịch theo mã, không hiển thị chuỗi do AI viết.
public enum CertificateWarningCode {
    // Chứng chỉ chưa có ảnh nào.
    NO_IMAGE,
    // Chỉ có file AI không đọc được (PDF…).
    UNSUPPORTED_FILE,
    // Có ảnh nhưng AI không đọc được nội dung.
    UNREADABLE,
    // Giá trị khai báo khác giá trị đọc được trên ảnh.
    MISMATCH,
    // Trường đã khai báo nhưng không tìm thấy trên ảnh.
    NOT_FOUND,
    // Chứng chỉ đã hết hạn.
    EXPIRED
}
