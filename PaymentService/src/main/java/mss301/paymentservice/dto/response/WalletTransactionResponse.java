package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletTransactionResponse {
    UUID id;

    UUID walletId;

    WalletTransactionType type;

    BigDecimal amount;

    BigDecimal availableBefore;

    BigDecimal availableAfter;

    BigDecimal heldBefore;

    BigDecimal heldAfter;

    UUID centerId;

    UUID bookingId;

    UUID occurrenceId;

    UUID refundId;

    UUID withdrawalId;

    String note;

    Instant createdAt;
}
