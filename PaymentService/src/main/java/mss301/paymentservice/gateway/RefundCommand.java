package mss301.paymentservice.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundCommand(UUID refundId, String requestId, String transactionReference, String transactionNo,
                            Instant transactionDate, BigDecimal amount, boolean fullRefund, String createdBy,
                            String orderInfo) {
}
