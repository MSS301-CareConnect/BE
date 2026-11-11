package mss301.paymentservice.gateway;

public record RefundResult(Outcome outcome, String responseCode, String providerRequestId, String message) {

    // Kết quả hoàn tiền thành công.
    public static RefundResult succeeded(String responseCode, String providerRequestId) {
        return new RefundResult(Outcome.SUCCEEDED, responseCode, providerRequestId, null);
    }

    // Kết quả hoàn tiền lỗi tạm thời, có thể thử lại.
    public static RefundResult retryable(String responseCode, String providerRequestId, String message) {
        return new RefundResult(Outcome.RETRYABLE, responseCode, providerRequestId, message);
    }

    // Kết quả hoàn tiền bị từ chối hẳn.
    public static RefundResult rejected(String responseCode, String providerRequestId, String message) {
        return new RefundResult(Outcome.REJECTED, responseCode, providerRequestId, message);
    }

    public enum Outcome {
        SUCCEEDED,
        RETRYABLE,
        REJECTED
    }
}
