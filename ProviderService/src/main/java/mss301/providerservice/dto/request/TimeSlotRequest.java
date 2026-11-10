package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

// Khung giờ nửa mở [startAt, endAt).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TimeSlotRequest {

    @NotNull(message = "SLOT_RANGE_INVALID")
    Instant startAt;

    @NotNull(message = "SLOT_RANGE_INVALID")
    Instant endAt;
}
