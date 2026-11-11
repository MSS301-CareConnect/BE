package mss301.identityservice.service;

import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.enums.TokenType;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.UUID;

public interface JwtService {
    // Phát JWT theo loại token.
    Jwt issue(UserResponse user, long authzVersion, TokenType tokenType);

    // Phát access token gắn với phiên.
    Jwt issueAccess(UserResponse user, long authzVersion, String sessionId);

    // Token client-credentials: sub = client_uuid, roles=[SERVICE], không có refresh token.
    Jwt issueServiceAccess(UUID clientUuid, String clientId);

    // Sinh refresh token ngẫu nhiên.
    String issueOpaqueRefreshToken();

    // Băm token để lưu trên Redis.
    String hashToken(String token);

    // Giải mã và kiểm tra access token.
    Jwt parseAccess(String token);

    // Lấy userId từ token.
    UUID getUserId(Jwt jwt);

    // Lấy session id từ token.
    String getSessionId(Jwt jwt);

    // Lấy authz_version từ token.
    long getAuthzVersion(Jwt jwt);

    // JWK Set công khai (chỉ public key) cho endpoint JWKS.
    Map<String, Object> publicKeys();
}
