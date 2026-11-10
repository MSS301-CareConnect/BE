package mss301.providerservice.enums;

// Mức rủi ro AIService trả về cho một chứng chỉ (cùng giá trị với AIService.CertificateRiskLevel).
public enum CertificateRiskLevel {
    // Thông tin chính khớp và chứng chỉ chưa hết hạn.
    LOW,
    // Thông tin phụ (tên chứng chỉ, đơn vị cấp, ngày tháng) chưa khớp.
    MEDIUM,
    // Tên caregiver hoặc mã chứng chỉ không khớp, hoặc chứng chỉ đã hết hạn.
    HIGH,
    // AI không đọc được nội dung ảnh.
    UNDETERMINED
}
