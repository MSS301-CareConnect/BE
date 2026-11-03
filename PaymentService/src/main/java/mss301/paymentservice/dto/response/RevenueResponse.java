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

    // CR-4: phần cọc khách bị giữ — caregiver của buổi hưởng (không tính vào doanh thu trung tâm) / trung tâm hưởng.
    BigDecimal caregiverForfeitAmount;

    BigDecimal centerForfeitAmount;

    // Doanh thu thuần của trung tâm = đã thu − đã hoàn − phần cọc thuộc caregiver.
    BigDecimal totalRevenue;

    BigDecimal averagePayment;
}
