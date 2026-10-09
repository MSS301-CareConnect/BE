package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

// Caregiver đã được CaregiverPort xác minh — hai id luôn đi cùng nhau (Provider + Identity).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssignedCaregiver {
    UUID caregiverId;

    UUID caregiverUserId;

    String fullName;
}
