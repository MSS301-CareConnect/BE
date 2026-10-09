package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

// Đổi caregiver: chỉ nhận caregiver_id của Provider; user_id Identity lấy từ Provider, không nhận từ client.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverAssignRequest {
    @NotNull(message = "CAREGIVER_ID_REQUIRED")
    UUID caregiverId;
}
