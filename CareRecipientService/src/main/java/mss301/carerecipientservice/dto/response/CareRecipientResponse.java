package mss301.carerecipientservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.BloodType;
import mss301.carerecipientservice.enums.Gender;
import mss301.carerecipientservice.enums.IndependenceLevel;
import mss301.carerecipientservice.enums.MobilityLevel;
import mss301.carerecipientservice.enums.RecipientStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareRecipientResponse {
    UUID id;

    String fullName;

    LocalDate dateOfBirth;

    Gender gender;

    String phoneNumber;

    String avatarUrl;

    String addressLine;

    String ward;

    String district;

    String province;

    BigDecimal latitude;

    BigDecimal longitude;

    BloodType bloodType;

    BigDecimal heightCm;

    BigDecimal weightKg;

    String medicalHistory;

    String chronicConditions;

    String allergies;

    MobilityLevel mobilityLevel;

    IndependenceLevel independenceLevel;

    String dietaryNotes;

    String sleepHabits;

    String behaviorNotes;

    String careRequirements;

    RecipientStatus status;

    UUID createdBy;

    UUID updatedBy;

    long version;

    Instant createdAt;

    Instant updatedAt;
}
