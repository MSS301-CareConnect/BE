package mss301.carecatalogservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class PricePlanRequest {

    @NotNull(message = "CENTER_SERVICE_ID_REQUIRED")
    @JsonAlias({"centerServiceId", "centerOfferingId"})
    UUID centerOfferingId;

    @NotBlank(message = "PRICE_PLAN_NAME_REQUIRED")
    String planName;

    @NotNull(message = "PRICING_UNIT_REQUIRED")
    PricingUnit pricingUnit;

    @NotNull(message = "INVALID_DURATION")
    @Min(value = 1, message = "INVALID_DURATION")
    Integer durationMinutes;

    @NotNull(message = "INVALID_BASE_PRICE")
    @Min(value = 0, message = "INVALID_BASE_PRICE")
    BigDecimal basePrice;

    Boolean isActive;
}
