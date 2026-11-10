package mss301.providerservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// Bản sao request của BookingService POST /api/v1/booking-occurrences/busy-caregivers (C5.1).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BusyCaregiverClientRequest {
    // Id caregiver_profiles của Provider (Booking lưu đúng id này), ≤ 200.
    List<UUID> caregiverIds;

    // ≤ 60 khung.
    List<TimeSlotClientRequest> slots;
}
