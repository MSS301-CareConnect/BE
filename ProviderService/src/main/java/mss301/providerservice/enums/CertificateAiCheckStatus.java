package mss301.providerservice.enums;

// Trạng thái kiểm tra chứng chỉ bằng AI (bước 4). null = chưa yêu cầu (chứng chỉ chưa được trung tâm xác nhận).
public enum CertificateAiCheckStatus {
    // Chờ CertificateAiCheckScheduler gửi sang AIService.
    QUEUED,
    // Đang gọi AIService (có lease; quá hạn thì scheduler nhận lại).
    PROCESSING,
    // Đã có kết quả (ai_risk_level, ai_warnings, ai_extracted).
    COMPLETED,
    // Hết số lần thử hoặc AIService từ chối — admin có thể chạy lại.
    FAILED
}
