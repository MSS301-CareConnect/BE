package mss301.providerservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// C5.2 — BookingService kiểm caregiver có lịch rảnh cho mọi buổi trước khi tạo / phân công booking.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AvailabilityCheckRequest {

    @NotNull(message = "CAREGIVER_ID_REQUIRED")
    UUID caregiverId;

    @NotEmpty(message = "SLOTS_INVALID")
    @Size(max = 60, message = "SLOTS_INVALID")
    List<@Valid @NotNull(message = "SLOT_RANGE_INVALID") TimeSlotRequest> slots;
}
