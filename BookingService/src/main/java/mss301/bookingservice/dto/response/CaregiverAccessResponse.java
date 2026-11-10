package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

// Kết quả /internal/bookings/caregiver-access (CareRecipientService hỏi trước khi cho caregiver xem hồ sơ).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverAccessResponse {
    UUID caregiverUserId;

    UUID recipientId;

    boolean allowed;
}
