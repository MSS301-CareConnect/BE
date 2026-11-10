package mss301.identityservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

// Token client-credentials trả trong body theo thiết kế (như /auth/mobile/*) — không có refresh token.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceTokenResponse {
    @ToString.Exclude
    String accessToken;

    String tokenType;

    long expiresInSeconds;
}
