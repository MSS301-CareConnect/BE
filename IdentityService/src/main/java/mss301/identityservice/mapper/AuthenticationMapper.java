package mss301.identityservice.mapper;

import mss301.identityservice.dto.response.AuthenticationResponse;
import mss301.identityservice.dto.response.CsrfResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.security.web.csrf.CsrfToken;

@Mapper(config = MapStructConfig.class)
public interface AuthenticationMapper {
    // Web: token nằm trong HttpOnly cookie, body chỉ có CSRF token mới.
    @Mapping(target = "authenticated", constant = "true")
    @Mapping(target = "accessToken", ignore = true)
    @Mapping(target = "refreshToken", ignore = true)
    AuthenticationResponse toWebResponse(String csrfToken, long expiresInSeconds);

    // Mobile: token trả trong body để app lưu vào OS secure storage.
    @Mapping(target = "authenticated", constant = "true")
    @Mapping(target = "csrfToken", ignore = true)
    AuthenticationResponse toMobileResponse(String accessToken, String refreshToken, long expiresInSeconds);

    CsrfResponse toCsrfResponse(CsrfToken csrfToken);
}
