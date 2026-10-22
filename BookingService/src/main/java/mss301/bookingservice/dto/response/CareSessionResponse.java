package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CareSessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Ca chăm sóc + checklist (OPS-02 / OPS-04).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CareSessionResponse {
    UUID sessionId;

    UUID occurrenceId;

    UUID bookingId;

    UUID caregiverId;

    CareSessionStatus status;

    Instant checkInAt;

    Instant checkOutAt;

    UUID endedBy;

    String endReason;

    List<CareTaskResponse> tasks;
}
