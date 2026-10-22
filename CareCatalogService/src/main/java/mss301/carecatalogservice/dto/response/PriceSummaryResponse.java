package mss301.carecatalogservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

// Giá thấp nhất (trong khoảng lọc) của một trung tâm — ProviderService giao với danh sách trung tâm ACTIVE.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PriceSummaryResponse {
    UUID centerId;

    BigDecimal minPrice;
}
