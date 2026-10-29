package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

// PAY-15 tối thiểu (D15): doanh thu = tiền đã thu − tiền đã hoàn.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RevenueResponse {
    long successfulPaymentCount;

    BigDecimal grossRevenue;

    BigDecimal totalRefunded;

    BigDecimal totalRevenue;

    BigDecimal averagePayment;
}
