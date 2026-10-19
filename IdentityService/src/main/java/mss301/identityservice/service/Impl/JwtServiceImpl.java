package mss301.identityservice.service.Impl;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.config.IdentityProperties;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.enums.TokenType;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.security.TokenClaims;
import mss301.identityservice.service.JwtService;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtServiceImpl implements JwtService {

    static SecureRandom SECURE_RANDOM = new SecureRandom();

    JwtEncoder jwtEncoder;
    NimbusJwtDecoder identityJwtDecoder;
    RSAKey signingKey;
    IdentityProperties identityProperties;

    @Override
    public Jwt issue(UserResponse user, long authzVersion, TokenType tokenType) {
        return issueInternal(user, authzVersion, tokenType, null);
    }

    @Override
    public Jwt issueAccess(UserResponse user, long authzVersion, String sessionId) {
        return issueInternal(user, authzVersion, TokenType.ACCESS, sessionId);
    }

    @Override
    public String issueOpaqueRefreshToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Override
    public String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public Jwt parseAccess(String token) {
        return parse(token, TokenType.ACCESS);
    }

    @Override
    public Jwt parseRefresh(String token) {
        return parse(token, TokenType.REFRESH);
    }

    @Override
    public UUID getUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    @Override
    public String getSessionId(Jwt jwt) {
        return jwt.getClaimAsString(TokenClaims.SESSION_ID);
    }

    @Override
    public long getAuthzVersion(Jwt jwt) {
        Number authzVersion = jwt.getClaim(TokenClaims.AUTHZ_VERSION);
        return authzVersion != null ? authzVersion.longValue() : 0L;
    }

    @Override
    public Map<String, Object> publicKeys() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }

    private Jwt issueInternal(UserResponse user, long authzVersion, TokenType tokenType, String sessionId) {
        Instant now = Instant.now();
        Duration ttl = tokenType == TokenType.ACCESS ? identityProperties.getAccessTtl() : identityProperties.getRefreshTtl();
        // Refresh token chỉ dùng tại IdentityService → không mang audience chung của các service backend.
        List<String> audience = tokenType == TokenType.ACCESS
                ? List.of(identityProperties.getAudience(), TokenClaims.API_AUDIENCE)
                : List.of(identityProperties.getAudience());
        // sub = userId (ổn định kể cả khi đổi email); không nhét dữ liệu nhạy cảm vào JWT.
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(identityProperties.getIssuer())
                .subject(user.getId().toString())
                .audience(audience)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.TOKEN_TYPE, tokenType.getValue())
                .claim(TokenClaims.AUTHZ_VERSION, authzVersion);

        if (tokenType == TokenType.ACCESS) {
            claims.claim(TokenClaims.ROLES, user.getRoles())
                    .claim(TokenClaims.PERMISSIONS, user.getPermissions());
            if (user.getCenterId() != null) {
                claims.claim(TokenClaims.CENTER_ID, user.getCenterId().toString());
            }
            if (sessionId != null) {
                claims.claim(TokenClaims.SESSION_ID, sessionId);
            }
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(identityProperties.getKeyId())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build()));
    }

    private Jwt parse(String token, TokenType expectedType) {
        Jwt jwt;
        try {
            jwt = identityJwtDecoder.decode(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (!expectedType.getValue().equals(jwt.getClaimAsString(TokenClaims.TOKEN_TYPE))) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return jwt;
    }
}
