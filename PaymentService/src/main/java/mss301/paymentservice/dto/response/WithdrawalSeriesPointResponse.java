package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

// Một mốc biểu đồ rút tiền: period = yyyy-MM-dd (DAY) hoặc yyyy-MM (MONTH), giờ Việt Nam.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WithdrawalSeriesPointResponse {
    String period;

    BigDecimal requested;

    BigDecimal completed;
}
