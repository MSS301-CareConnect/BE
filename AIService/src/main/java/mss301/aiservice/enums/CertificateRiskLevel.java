package mss301.aiservice.enums;

/**
 * Mức rủi ro của một chứng chỉ sau khi AI đọc ảnh và đối chiếu với thông tin caregiver khai báo.
 * Do code tính từ kết quả đối chiếu (CertificateRiskServiceImpl), không để mô hình tự phán.
 */
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
