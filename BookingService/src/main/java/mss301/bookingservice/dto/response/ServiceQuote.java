package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.UnitType;

import java.math.BigDecimal;
import java.util.UUID;

// Một dòng dịch vụ đã báo giá bởi CareServiceCatalogPort (tên / giá NULL khi chưa nối Catalog).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceQuote {
    UUID careServiceId;

    UUID centerServiceId;

    UUID pricePlanId;

    String serviceName;

    UnitType unitType;

    BigDecimal unitPrice;

    Integer quantity;

    BigDecimal totalPrice;
}
