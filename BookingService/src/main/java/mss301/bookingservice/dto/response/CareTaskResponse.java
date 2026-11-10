package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CareTaskStatus;
import mss301.bookingservice.enums.CareTaskType;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

// Một việc trong checklist của ca.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CareTaskResponse {
    UUID id;

    CareTaskType taskType;

    String title;

    UUID careServiceId;

    LocalTime scheduledTime;

    CareTaskStatus status;

    String skipReason;

    String note;

    Instant completedAt;

    Integer sortOrder;
}
