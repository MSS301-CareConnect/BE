package mss301.paymentservice.gateway;

/**
 * Kết quả querydr: PAID (đã thu, kèm mã giao dịch / số tiền ×100), NOT_PAID (chắc chắn chưa thu), UNKNOWN (VNPay lỗi /
 * không trả lời — để lượt sau hỏi lại).
 */
public record QueryResult(Status status, String transactionNo, String amountMinor, String responseCode) {

    public enum Status {
        PAID,
        NOT_PAID,
        UNKNOWN
    }
}
