package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.UnitType;

import java.math.BigDecimal;
import java.util.List;
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

    String planName;

    UnitType unitType;

    BigDecimal unitPrice;

    // Thời lượng chuẩn của gói (HOUR luôn 60; DAY / SHIFT là độ dài đơn vị) — dùng để kiểm buổi theo ca.
    Integer planDurationMinutes;

    BigDecimal unitsPerOccurrence;

    Integer occurrenceCount;

    // Tổng số đơn vị trên cả booking (Σ đơn vị mỗi buổi).
    BigDecimal quantity;

    BigDecimal totalPrice;

    // Các nhóm buổi theo số đơn vị mỗi buổi, tăng dần (chỉ để hiển thị báo giá; không lưu vào booking_details).
    List<QuoteUnitGroup> unitGroups;
}
