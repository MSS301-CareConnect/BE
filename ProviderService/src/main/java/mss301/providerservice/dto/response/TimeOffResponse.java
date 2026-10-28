package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TimeOffResponse {
    UUID id;
    UUID caregiverId;
    Instant startAt;
    Instant endAt;
    String reason;
    UUID createdBy;
    Long version;
    Instant createdAt;
    Instant updatedAt;
}
