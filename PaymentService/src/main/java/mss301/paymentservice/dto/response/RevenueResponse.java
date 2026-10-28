package mss301.paymentservice.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueResponse {
    long successfulPaymentCount;
    BigDecimal totalRevenue;
    BigDecimal averagePayment;
}
