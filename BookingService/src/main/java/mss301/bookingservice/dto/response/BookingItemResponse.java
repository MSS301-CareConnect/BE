package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.UnitType;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingItemResponse {
    UUID id;

    UUID careServiceId;

    UUID centerServiceId;

    UUID pricePlanId;

    // NULL tới khi nối CareCatalogService
    String serviceName;

    UnitType unitType;

    BigDecimal unitPrice;

    Integer quantity;

    Integer unitsPerOccurrence;

    BigDecimal totalPrice;
}
