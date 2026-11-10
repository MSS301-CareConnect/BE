package mss301.providerservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

// Khung giờ [startAt, endAt) gửi BookingService (C5.1).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TimeSlotClientRequest {
    Instant startAt;

    Instant endAt;
}
