package mss301.paymentservice.dto.request;

import mss301.paymentservice.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lệnh nội bộ ghi một biến động ví (không phải request API): WalletService.post khóa ví, kiểm trùng referenceKey, đổi số
 * dư theo hệ số của type rồi ghi một dòng wallet_transactions từ các giá trị này.
 */
public record WalletPosting(WalletTransactionType type, BigDecimal amount, String referenceKey, UUID centerId,
                            UUID bookingId, UUID occurrenceId, UUID refundId, UUID withdrawalId, String note) {
}
