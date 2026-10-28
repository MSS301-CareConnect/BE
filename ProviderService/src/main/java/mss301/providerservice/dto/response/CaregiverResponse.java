package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.Gender;
import mss301.providerservice.enums.VerificationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverResponse {
    UUID id;
    UUID userId;
    UUID centerId;
    String centerName;
    CenterStatus centerStatus;
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
    // Kỹ năng (PRV-01): id dịch vụ chuẩn (Catalog item) và id offering của trung tâm; chưa gán → [].
    List<UUID> careServiceIds;
    List<UUID> centerServiceIds;

    public UUID getCaregiverId() {
        return id;
    }
}
