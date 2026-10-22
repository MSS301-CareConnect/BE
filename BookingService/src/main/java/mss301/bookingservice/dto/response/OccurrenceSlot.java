package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

// Một buổi do OccurrenceGenerator sinh (UTC) — nội bộ, chưa phải entity.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OccurrenceSlot {
    int occurrenceNo;

    Instant startAt;

    Instant endAt;

    int durationMinutes;

    // Giá buổi = Σ dịch vụ (đơn giá × số đơn vị của buổi), làm tròn đồng — BookingPriceCalculator gán (BK-01).
    BigDecimal priceAmount;
}
