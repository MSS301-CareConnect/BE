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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtServiceImpl implements JwtService {

    JwtEncoder jwtEncoder;
    NimbusJwtDecoder identityJwtDecoder;
    RSAKey signingKey;
    IdentityProperties identityProperties;

    @Override
    public Jwt issue(UserResponse user, long authzVersion, TokenType tokenType) {
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
        // Refresh token không mang quyền: quyền luôn lấy mới từ DB khi refresh.
        if (tokenType == TokenType.ACCESS) {
            claims.claim(TokenClaims.ROLES, user.getRoles())
                    .claim(TokenClaims.PERMISSIONS, user.getPermissions());
            if (user.getCenterId() != null) {
                claims.claim(TokenClaims.CENTER_ID, user.getCenterId().toString());
            }
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(identityProperties.getKeyId())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build()));
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
    public long getAuthzVersion(Jwt jwt) {
        Number authzVersion = jwt.getClaim(TokenClaims.AUTHZ_VERSION);
        return authzVersion.longValue();
    }

    @Override
    public Map<String, Object> publicKeys() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
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
