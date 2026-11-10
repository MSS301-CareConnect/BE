package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.WalletOwnerType;

import java.math.BigDecimal;

// Admin: tổng số dư đang giữ hộ theo loại chủ ví (constructor dùng trong JPQL).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletBalanceTotalResponse {
    WalletOwnerType ownerType;

    Long walletCount;

    BigDecimal availableBalance;

    BigDecimal heldBalance;
}
