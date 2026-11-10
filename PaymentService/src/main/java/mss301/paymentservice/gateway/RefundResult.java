package mss301.paymentservice.gateway;

/**
 * Kết quả gọi cổng hoàn tiền. RETRYABLE (timeout, 5xx, VNPay 94) → giữ REFUND_PENDING + backoff; REJECTED (lỗi nghiệp vụ)
 * → FAILED ngay. message không chứa secret.
 */
public record RefundResult(Outcome outcome, String responseCode, String providerRequestId, String message) {

    public static RefundResult succeeded(String responseCode, String providerRequestId) {
        return new RefundResult(Outcome.SUCCEEDED, responseCode, providerRequestId, null);
    }

    public static RefundResult retryable(String responseCode, String providerRequestId, String message) {
        return new RefundResult(Outcome.RETRYABLE, responseCode, providerRequestId, message);
    }

    public static RefundResult rejected(String responseCode, String providerRequestId, String message) {
        return new RefundResult(Outcome.REJECTED, responseCode, providerRequestId, message);
    }

    public enum Outcome {
        SUCCEEDED,
        RETRYABLE,
        REJECTED
    }
}
