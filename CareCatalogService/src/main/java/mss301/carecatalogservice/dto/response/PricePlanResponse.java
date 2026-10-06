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
    UUID centerServiceId;
    String planName;
    PricingUnit pricingUnit;
    Integer durationMinutes;
    BigDecimal basePrice;
    Boolean isActive;
}
