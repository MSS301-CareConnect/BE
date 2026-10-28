package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.RegistrationStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShiftRegistrationResponse {
    UUID id;
    UUID workShiftId;
    UUID caregiverId;
    String caregiverName;
    RegistrationStatus registrationStatus;
    String caregiverNote;
    UUID reviewedBy;
    Instant reviewedAt;
    String rejectionReason;
    Long version;
    Instant createdAt;
    Instant updatedAt;
}
