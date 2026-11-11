package mss301.aiservice.enums;

public enum CertificateRiskLevel {
    // Thông tin chính khớp và chứng chỉ chưa hết hạn.
    LOW,
    // Thông tin phụ (tên chứng chỉ, đơn vị cấp, ngày tháng) chưa khớp, hoặc trường đã khai không thấy trên ảnh.
    MEDIUM,
    // Tên caregiver hoặc mã chứng chỉ không khớp, hoặc chứng chỉ đã hết hạn.
    HIGH,
    // AI không đọc được nội dung ảnh (không có ảnh, chỉ có PDF, ảnh mờ…).
    UNDETERMINED
}
