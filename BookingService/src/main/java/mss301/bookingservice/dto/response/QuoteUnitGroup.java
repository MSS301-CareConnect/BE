package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

// Một nhóm buổi cùng số đơn vị trong một dòng dịch vụ của báo giá (vd 14 buổi × 2 giờ, 1 buổi × 3 giờ) và thành tiền
// của nhóm — để khách thấy rõ buổi nào dài hơn tính bao nhiêu tiền.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QuoteUnitGroup {
    BigDecimal unitsPerOccurrence;

    Integer occurrenceCount;

    BigDecimal amount;
}
