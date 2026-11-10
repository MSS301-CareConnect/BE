package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

// Khung rảnh [startAt, endAt) đã trừ ngày nghỉ (chưa trừ buổi đã đặt — Booking là nguồn sự thật về giờ đã đặt).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AvailabilityWindowResponse {
    Instant startAt;
    Instant endAt;
}
