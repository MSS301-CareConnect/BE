package mss301.bookingservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Bản sao request C5.2 POST /api/v1/caregivers/availability-checks của ProviderService (tối đa 60 khoảng / lần).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AvailabilityCheckRequest {
    UUID caregiverId;

    List<Slot> slots;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Slot {
        Instant startAt;

        Instant endAt;
    }
}
