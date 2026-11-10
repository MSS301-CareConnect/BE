package mss301.bookingservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;
import mss301.bookingservice.enums.OverallCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareReportRequest {
    @NotNull(message = "INVALID_REQUEST")
    UUID sessionId;
    @NotNull(message = "INVALID_REQUEST")
    OverallCondition overallCondition;
    @NotBlank(message = "INVALID_REQUEST")
    @Size(max = 4000, message = "INVALID_REQUEST")
    String summary;
    @Size(max = 2000, message = "INVALID_REQUEST")
    String mealNotes;
    @Size(max = 2000, message = "INVALID_REQUEST")
    String medicationNotes;
    @Size(max = 2000, message = "INVALID_REQUEST")
    String mobilityNotes;
    @Size(max = 2000, message = "INVALID_REQUEST")
    String moodNotes;
    @Size(max = 2000, message = "INVALID_REQUEST")
    String recommendations;
    Long version;
}
