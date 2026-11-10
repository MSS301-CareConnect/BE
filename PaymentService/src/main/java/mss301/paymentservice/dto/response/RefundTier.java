package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

// Một mốc phí hủy: buổi còn [minLeadHours, maxLeadHours) giờ → phí feePercent % giá buổi; maxLeadHours null = vô cực.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefundTier {
    int minLeadHours;

    Integer maxLeadHours;

    BigDecimal feePercent;
}
