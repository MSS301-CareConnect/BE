package mss301.aiservice.security;

import mss301.aiservice.config.AIProperties;
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

    @Bean
    public JwtDecoder jwtDecoder(AIProperties aiProperties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                        .withJwkSetUri(aiProperties.getJwt().getJwksUri())
                        .jwsAlgorithm(SignatureAlgorithm.RS256)
                        .build();

        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> hasRequiredClaims(jwt)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Invalid token claims", null));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(aiProperties.getJwt().getIssuer()), requiredClaims));

        return decoder;
    }

    private boolean hasRequiredClaims(Jwt jwt) {
        boolean valid = jwt.getAudience() != null && jwt.getAudience().contains(TokenClaims.API_AUDIENCE)
                && TokenClaims.ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString(TokenClaims.TOKEN_TYPE))
                && jwt.getExpiresAt() != null
                && jwt.getIssuedAt() != null
                && jwt.getId() != null;

        return valid && isUuid(jwt.getSubject());
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;

        } catch (RuntimeException exception) {
            return false;
        }
    }
}
