package mss301.providerservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

// Bản sao request của IdentityService POST /api/v1/auth/service-token (C1.1).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceTokenRequest {
    String clientId;

    @ToString.Exclude
    String clientSecret;
}
