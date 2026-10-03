package mss301.carerecipientservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmergencyContactResponse {
    UUID id;

    String fullName;

    String relationship;

    String phoneNumber;

    boolean primary;

    int priority;

    Instant createdAt;

    Instant updatedAt;
}
