package mss301.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CareCatalogQuoteResponse {

    UUID careServiceId;

    UUID centerServiceId;

    UUID pricePlanId;

    String serviceName;

    String pricingUnit;

    Integer durationMinutes;

    BigDecimal basePrice;

    // Đơn giá sau giảm % của gói (CAT-03). Catalog bản cũ không gửi → dùng basePrice.
    BigDecimal finalPrice;

    boolean active;
}
