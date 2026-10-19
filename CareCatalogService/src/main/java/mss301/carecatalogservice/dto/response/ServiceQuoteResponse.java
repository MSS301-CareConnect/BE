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
public class ServiceQuoteResponse {

    UUID careServiceId;

    UUID centerServiceId;

    UUID pricePlanId;

    String serviceName;

    PricingUnit pricingUnit;

    Integer durationMinutes;

    BigDecimal basePrice;

    boolean active;
}
