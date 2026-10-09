package mss301.carerecipientservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

// Bản sao CaregiverAccessResponse của BookingService (GET /internal/bookings/caregiver-access).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CaregiverAccessResponse {
    UUID caregiverUserId;

    UUID recipientId;

    boolean allowed;
}
