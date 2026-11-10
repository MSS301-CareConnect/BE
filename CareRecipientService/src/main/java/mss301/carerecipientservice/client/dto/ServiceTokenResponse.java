package mss301.carerecipientservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

// Bản sao response của IdentityService POST /api/v1/auth/service-token (C1.1).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceTokenResponse {
    @ToString.Exclude
    String accessToken;

    String tokenType;

    long expiresInSeconds;
}
