package mss301.carecatalogservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.PricingUnit;

import java.math.BigDecimal;

// Gói giá gửi kèm khi tạo dịch vụ trung tâm (CenterOfferingRequest.pricePlans) — như PricePlanRequest nhưng chưa có
// centerOfferingId; tạo cùng transaction với dịch vụ.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PricePlanDraftRequest {

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

    @Min(value = 0, message = "INVALID_DISCOUNT_PERCENT")
    @Max(value = 90, message = "INVALID_DISCOUNT_PERCENT")
    Integer discountPercent;

    Boolean isActive;
}
