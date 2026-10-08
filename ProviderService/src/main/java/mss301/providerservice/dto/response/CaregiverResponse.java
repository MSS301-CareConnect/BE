package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverResponse {
    UUID id;
    UUID userId;
    UUID centerId;
    String centerName;
    String fullName;
    Gender gender;
    LocalDate dateOfBirth;
    Integer experienceYears;
    String bio;
    BigDecimal ratingAverage;
    Integer ratingCount;
    VerificationStatus verificationStatus;
    CaregiverStatus caregiverStatus;
    String verificationReason;
    UUID verifiedBy;
    Instant verifiedAt;
    UUID createdBy;
    UUID updatedBy;
    Long version;
    Instant createdAt;
    Instant updatedAt;
}
