package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecipientSnapshot {
    public static final String STATUS_ACTIVE = "ACTIVE";

    UUID recipientId;

    String fullName;

    LocalDate dateOfBirth;

    Integer age;

    String gender;

    String phoneNumber;

    String addressLine;

    String ward;

    String district;

    String province;

    BigDecimal latitude;

    BigDecimal longitude;

    String bloodType;

    String mobilityLevel;

    String independenceLevel;

    String allergies;

    String chronicConditions;

    String dietaryNotes;

    String behaviorNotes;

    String careRequirements;

    String status;

    List<MedicationItem> activeMedications;

    // null khi hồ sơ chưa có liên hệ chính
    EmergencyContactItem primaryEmergencyContact;

    Instant snapshotAt;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MedicationItem {
        UUID id;

        String medicationName;

        String dosage;

        String frequency;

        List<String> timeOfDay;

        String route;

        LocalDate startDate;

        LocalDate endDate;

        String usageNotes;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmergencyContactItem {
        String fullName;

        String relationship;

        String phoneNumber;
    }
}
