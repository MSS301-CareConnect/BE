package mss301.carerecipientservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.MedicationRoute;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MedicationResponse {
    UUID id;

    String medicationName;

    String dosage;

    String frequency;

    List<String> timeOfDay;

    MedicationRoute route;

    LocalDate startDate;

    LocalDate endDate;

    String prescribedBy;

    String usageNotes;

    boolean active;

    UUID updatedBy;

    Instant createdAt;

    Instant updatedAt;
}
