package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.WalletOwnerType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Ví chưa có giao dịch nào: id null, số dư 0 (ví chỉ được tạo ở biến động đầu tiên).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletResponse {
    UUID id;

    WalletOwnerType ownerType;

    UUID ownerId;

    BigDecimal availableBalance;

    BigDecimal heldBalance;

    // Ví nền tảng không rút; ví trung tâm âm (nợ do hoàn khiếu nại) cũng không rút được.
    boolean withdrawable;

    Instant updatedAt;
}
