package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.WalletTransactionType;

import java.math.BigDecimal;

// Tổng theo loại biến động trong kỳ (constructor dùng trong JPQL — thứ tự field là hợp đồng của query).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletTypeTotalResponse {
    WalletTransactionType type;

    Long count;

    BigDecimal totalAmount;
}
