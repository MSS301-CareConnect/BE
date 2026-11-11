package mss301.mediaservice.security;

import mss301.mediaservice.config.MediaProperties;
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

@Configuration
public class JwtConfig {

    // Cấu hình bộ giải mã JWT qua JWKS và các validator issuer, audience, claim.
    @Bean
    public JwtDecoder jwtDecoder(MediaProperties mediaProperties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(mediaProperties.getJwt().getJwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> hasRequiredClaims(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token claims", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(mediaProperties.getJwt().getIssuer()), requiredClaims));
        return decoder;
    }

    // Kiểm tra JWT là access token có đủ claim bắt buộc.
    private static boolean hasRequiredClaims(Jwt jwt) {
        boolean valid = jwt.getAudience() != null
                && jwt.getAudience().contains(TokenClaims.API_AUDIENCE)
                && TokenClaims.ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString(TokenClaims.TOKEN_TYPE))
                && jwt.getExpiresAt() != null
                && jwt.getIssuedAt() != null
                && jwt.getId() != null;
        return valid && isUuid(jwt.getSubject());
    }

    // Kiểm tra chuỗi có phải UUID hợp lệ.
    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
