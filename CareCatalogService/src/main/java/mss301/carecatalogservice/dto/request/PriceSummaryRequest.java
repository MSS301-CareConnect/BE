package mss301.carecatalogservice.dto.request;

import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

// Query của GET /api/v1/center-services/price-summary (C9.2, ProviderService gọi bằng token SERVICE cho SRCH-02).
// Trung tâm khớp khi có ít nhất một gói giá đang bán (của dịch vụ đã chọn, nếu có) nằm trong [minPrice, maxPrice].
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PriceSummaryRequest {
    UUID careServiceId;

    @Min(value = 0, message = "PRICE_RANGE_INVALID")
    BigDecimal minPrice;

    @Min(value = 0, message = "PRICE_RANGE_INVALID")
    BigDecimal maxPrice;
}
