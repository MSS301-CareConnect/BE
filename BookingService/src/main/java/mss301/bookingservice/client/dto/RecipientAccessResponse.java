package mss301.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

// Bản sao AccessCheckResponse của CareRecipientService (GET /internal/care-recipients/{id}/access).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecipientAccessResponse {
    UUID recipientId;

    UUID userId;

    boolean allowed;

    String accessLevel;
}
