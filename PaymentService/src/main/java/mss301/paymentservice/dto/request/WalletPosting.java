package mss301.paymentservice.dto.request;

import mss301.paymentservice.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletPosting(WalletTransactionType type, BigDecimal amount, String referenceKey, UUID centerId,
                            UUID bookingId, UUID occurrenceId, UUID refundId, UUID withdrawalId, String note) {
}
