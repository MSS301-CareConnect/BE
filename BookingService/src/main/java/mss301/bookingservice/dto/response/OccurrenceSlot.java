package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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

    // SHIFT: lịch caregiver (ProviderService) mà buổi này giữ; RECURRING / CUSTOM để trống.
    UUID caregiverScheduleId;

    // Dịch vụ của buổi (NULL = mọi dịch vụ của booking).
    UUID careServiceId;

    // SHIFT: số booking tối đa trên ca (ProviderService) — chỉ dùng để kiểm lúc tạo, không lưu.
    Integer scheduleMaxBookings;

    // Giá buổi = Σ dịch vụ (đơn giá × số đơn vị của buổi), làm tròn đồng — BookingPriceCalculator gán (BK-01).
    BigDecimal priceAmount;
}
