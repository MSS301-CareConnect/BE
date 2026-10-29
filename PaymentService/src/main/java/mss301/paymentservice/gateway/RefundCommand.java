package mss301.paymentservice.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Dữ liệu một lần gọi hoàn tiền, dựng trong transaction "nhận việc" rồi dùng NGOÀI transaction (rule #24).
 *
 * @param fullRefund hoàn toàn bộ payment một lần (VNPay vnp_TransactionType 02), ngược lại một phần (03)
 */
public record RefundCommand(UUID refundId, String requestId, String transactionReference, String transactionNo,
                            Instant transactionDate, BigDecimal amount, boolean fullRefund, String createdBy,
                            String orderInfo) {
}
