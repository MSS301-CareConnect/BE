package mss301.providerservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

// Bản sao phần tử response của CareCatalogService GET /api/v1/center-services/price-summary (C9.2).
// serviceCount không có trong contract C9.2 — đọc nếu Catalog trả, không có thì null.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CenterPriceSummaryClientResponse {
    UUID centerId;

    BigDecimal minPrice;

    Integer serviceCount;
}
