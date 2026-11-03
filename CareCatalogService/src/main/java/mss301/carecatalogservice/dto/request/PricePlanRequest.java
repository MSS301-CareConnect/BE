package mss301.carecatalogservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
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

    // 0–90 %. Không gửi: tạo mới = 0, sửa = giữ mức đang lưu (client cũ không xóa mất giảm giá).
    @Min(value = 0, message = "INVALID_DISCOUNT_PERCENT")
    @Max(value = 90, message = "INVALID_DISCOUNT_PERCENT")
    Integer discountPercent;

    Boolean isActive;

    // M-04: version lúc người dùng mở form; có thì phải khớp bản đang lưu (khác → 409). Bỏ trống = client cũ, không kiểm.
    Long version;
}
