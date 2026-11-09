package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

// Một điểm biểu đồ: period = yyyy-MM-dd (DAY) hoặc yyyy-MM (MONTH), giờ Việt Nam. spent chỉ có ở ví khách.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletSeriesPointResponse {
    String period;

    BigDecimal income;

    BigDecimal withdrawn;

    BigDecimal spent;
}
