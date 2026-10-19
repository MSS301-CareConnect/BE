package mss301.identityservice.service;

import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.enums.TokenType;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.UUID;

public interface JwtService {
    Jwt issue(UserResponse user, long authzVersion, TokenType tokenType);

    Jwt issueAccess(UserResponse user, long authzVersion, String sessionId);

    String issueOpaqueRefreshToken();

    String hashToken(String token);

    Jwt parseAccess(String token);

    Jwt parseRefresh(String token);

    UUID getUserId(Jwt jwt);

    String getSessionId(Jwt jwt);

    long getAuthzVersion(Jwt jwt);

    // JWK Set công khai (chỉ public key) cho endpoint JWKS.
    Map<String, Object> publicKeys();
}
