package mss301.bookingservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// C5.1 POST /api/v1/booking-occurrences/busy-caregivers (ProviderService gọi bằng token SERVICE khi tìm caregiver /
// tạo ngày nghỉ). caregiverIds = caregiver_profiles.caregiver_id của Provider.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BusyCaregiverRequest {
    @NotEmpty(message = "CAREGIVER_IDS_REQUIRED")
    @Size(max = 200, message = "TOO_MANY_CAREGIVER_IDS")
    List<@NotNull(message = "CAREGIVER_IDS_REQUIRED") UUID> caregiverIds;

    @NotEmpty(message = "SLOTS_REQUIRED")
    @Size(max = 60, message = "TOO_MANY_SLOTS")
    List<@Valid @NotNull(message = "SLOTS_REQUIRED") TimeSlotRequest> slots;
}
