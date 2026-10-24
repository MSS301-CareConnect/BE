package mss301.bookingservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mss301.bookingservice.enums.CareTaskStatus;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareTaskUpdateRequest {
    @NotNull(message = "INVALID_REQUEST")
    CareTaskStatus status;
    @Size(max = 1000, message = "INVALID_REQUEST")
    String note;
    @Size(max = 500, message = "TASK_SKIP_REASON_REQUIRED")
    String skipReason;
}
