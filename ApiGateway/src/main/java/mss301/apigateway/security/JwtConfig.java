package mss301.apigateway.security;

import mss301.apigateway.config.GatewayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.UUID;

/**
 * Kiểm token SỚM tại gateway (offline, JWKS của Identity — được cache): RS256 + iss + aud chứa careconnect-api + exp/iat
 * + jti + sub UUID + token_type=access. Service phía sau vẫn tự xác minh; gateway không kiểm blacklist/trạng thái tài
 * khoản (đó là việc của IdentityService).
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(GatewayProperties gatewayProperties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(gatewayProperties.getJwt().getJwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> hasRequiredClaims(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token claims", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(gatewayProperties.getJwt().getIssuer()), requiredClaims));
        return decoder;
    }

    private static boolean hasRequiredClaims(Jwt jwt) {
        boolean valid = jwt.getAudience() != null
                && jwt.getAudience().contains(TokenClaims.API_AUDIENCE)
                && TokenClaims.ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString(TokenClaims.TOKEN_TYPE))
                && jwt.getExpiresAt() != null
                && jwt.getIssuedAt() != null
                && jwt.getId() != null;
        return valid && isUuid(jwt.getSubject());
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
