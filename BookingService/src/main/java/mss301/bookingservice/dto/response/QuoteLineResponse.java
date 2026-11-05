package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.UnitType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Một dòng dịch vụ trong báo giá: đơn giá × số đơn vị mỗi buổi × số buổi.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuoteLineResponse {
    UUID careServiceId;

    String serviceName;

    // Gói giá khách chọn: tên + thời lượng chuẩn (phút) của một đơn vị.
    String planName;

    Integer planDurationMinutes;

    UnitType pricingUnit;

    BigDecimal unitPrice;

    // NULL khi các buổi có thời lượng khác nhau (giá theo giờ) — FE dùng quantity (tổng đơn vị mọi buổi).
    BigDecimal unitsPerOccurrence;

    BigDecimal quantity;

    // Buổi chia theo thời lượng (vd 2 giờ × 14 buổi, 3 giờ × 1 buổi) kèm thành tiền từng nhóm.
    List<QuoteUnitGroup> unitGroups;

    Integer occurrenceCount;

    BigDecimal lineTotal;
}
