package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

// Manager xác nhận (approved = true → CUSTOMER_NO_SHOW) hoặc bác (false → MISSED, hoàn tiền khách) báo vắng (OPS-06).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NoShowDecisionRequest {
    @NotNull(message = "NO_SHOW_DECISION_REQUIRED")
    Boolean approved;

    @Size(max = 1000, message = "TEXT_TOO_LONG")
    String note;
}
