package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.WalletOwnerType;

import java.math.BigDecimal;

// Thống kê rút tiền theo loại chủ ví: đang chờ (mọi thời điểm) và đã chuyển trong kỳ.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WithdrawalOwnerTotalResponse {
    WalletOwnerType ownerType;

    long pendingCount;

    BigDecimal pendingAmount;

    long completedCount;

    BigDecimal completedAmount;
}
