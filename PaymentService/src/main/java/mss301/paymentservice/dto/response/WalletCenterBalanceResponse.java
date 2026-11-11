package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

// Số dư khả dụng của ví khách / caregiver theo trung tâm đã tạo ra khoản tiền (hoàn / cọc giữ) — yêu cầu rút gửi về trung
// tâm đó xử lý. centerId null = tiền không gắn trung tâm (dữ liệu cũ), admin xử lý.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletCenterBalanceResponse {
    UUID centerId;

    BigDecimal availableBalance;
}
