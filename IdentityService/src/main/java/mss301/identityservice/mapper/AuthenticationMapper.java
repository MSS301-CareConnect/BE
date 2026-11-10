package mss301.identityservice.mapper;

import mss301.identityservice.dto.response.AuthenticationResponse;
import mss301.identityservice.dto.response.CsrfResponse;
import mss301.identityservice.dto.response.SessionResponse;
import java.time.Instant;
import mss301.identityservice.dto.response.ServiceTokenResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.security.web.csrf.CsrfToken;

@Mapper(config = MapStructConfig.class)
public interface AuthenticationMapper {
    SessionResponse toSessionResponse(String sessionId, String deviceId, String userAgent, String ip,
                                      Instant createdAt, Instant lastActiveAt, boolean current);

    // Web: token nằm trong HttpOnly cookie, body chỉ có CSRF token mới.
    @Mapping(target = "authenticated", constant = "true")
    @Mapping(target = "accessToken", ignore = true)
    @Mapping(target = "refreshToken", ignore = true)
    @Mapping(target = "user", ignore = true)
    AuthenticationResponse toWebResponse(String csrfToken, long expiresInSeconds);

    // Mobile: token trả trong body để app lưu vào OS secure storage.
    @Mapping(target = "authenticated", constant = "true")
    @Mapping(target = "csrfToken", ignore = true)
    @Mapping(target = "user", ignore = true)
    AuthenticationResponse toMobileResponse(String accessToken, String refreshToken, long expiresInSeconds);

    CsrfResponse toCsrfResponse(CsrfToken csrfToken);

    // Token SERVICE trả trong body theo thiết kế (client-credentials), không có refresh token.
    @Mapping(target = "tokenType", constant = "Bearer")
    ServiceTokenResponse toServiceTokenResponse(String accessToken, long expiresInSeconds);
}
