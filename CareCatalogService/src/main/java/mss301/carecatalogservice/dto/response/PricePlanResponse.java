package mss301.carecatalogservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.PricingUnit;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PricePlanResponse {
    UUID id;

    UUID centerOfferingId;

    String planName;

    PricingUnit pricingUnit;

    Integer durationMinutes;

    BigDecimal basePrice;

    // Giá gốc = basePrice; khách trả finalPrice = basePrice × (100 − discountPercent) / 100, làm tròn đến đồng.
    Integer discountPercent;

    BigDecimal finalPrice;

    Boolean isActive;

    Long version;
}
