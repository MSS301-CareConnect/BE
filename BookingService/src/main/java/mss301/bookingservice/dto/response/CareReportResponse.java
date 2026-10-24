package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import mss301.bookingservice.enums.CareReportStatus;
import mss301.bookingservice.enums.OverallCondition;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareReportResponse {
    UUID id;
    UUID sessionId;
    UUID occurrenceId;
    UUID bookingId;
    UUID caregiverUserId;
    OverallCondition overallCondition;
    String summary;
    String mealNotes;
    String medicationNotes;
    String mobilityNotes;
    String moodNotes;
    String recommendations;
    CareReportStatus status;
    Instant submittedAt;
    Long version;
    Instant createdAt;
    List<ReportPhotoResponse> photos;
}
