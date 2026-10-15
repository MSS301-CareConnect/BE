package mss301.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

// Bản sao InternalCenterResponse của ProviderService (GET /internal/centers/{id}).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderCenterResponse {
    public static final String STATUS_ACTIVE = "ACTIVE";

    UUID centerId;

    String centerName;

    String centerStatus;

    UUID managerUserId;
}
