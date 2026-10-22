package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.UnitType;

import java.math.BigDecimal;
import java.util.UUID;

// Một dòng dịch vụ: phần giá gốc lấy từ CareCatalogService (CareServiceCatalogPort), phần số lượng / thành tiền do
// BookingPriceCalculator tính theo các buổi (BK-01). Không bao giờ lấy từ client.
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

    // Thời lượng chuẩn của gói (HOUR luôn 60; DAY / SHIFT là độ dài đơn vị) — dùng để kiểm buổi theo ca.
    Integer planDurationMinutes;

    Integer unitsPerOccurrence;

    Integer occurrenceCount;

    // Tổng số đơn vị trên cả booking (Σ đơn vị mỗi buổi).
    Integer quantity;

    BigDecimal totalPrice;
}
