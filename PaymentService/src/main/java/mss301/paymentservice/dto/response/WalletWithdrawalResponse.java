package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletWithdrawalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletWithdrawalResponse {
    UUID id;

    UUID walletId;

    UUID requesterUserId;

    // Tên / SĐT từ Identity (2026-11-11) — chỉ có ở danh sách và chi tiết; null khi Identity không trả lời.
    String requesterName;

    String requesterPhoneNumber;

    WalletOwnerType ownerType;

    UUID centerId;

    BigDecimal amount;

    String bankName;

    String bankAccountNumber;

    String accountHolderName;

    WalletWithdrawalStatus status;

    String rejectReason;

    UUID reviewedBy;

    Instant reviewedAt;

    String transferReference;

    UUID completedBy;

    Instant completedAt;

    Instant createdAt;

    Instant updatedAt;
}
