package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.AvailabilityViolationReason;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AvailabilityViolationResponse {
    Instant startAt;
    Instant endAt;
    AvailabilityViolationReason reason;
}
