package mss301.paymentservice.security;

import mss301.paymentservice.config.PaymentProperties;
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

    // RS256 + issuer + audience careconnect-api + token_type=access + sub là UUID (AGENTS §4).
    @Bean
    JwtDecoder jwtDecoder(PaymentProperties paymentProperties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(paymentProperties.getJwt().getJwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> claimsValidator = jwt -> hasValidClaims(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token claims", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(paymentProperties.getJwt().getIssuer()), claimsValidator));
        return decoder;
    }

    private boolean hasValidClaims(Jwt jwt) {
        boolean validShape = jwt.getAudience() != null
                && jwt.getAudience().contains(TokenClaims.API_AUDIENCE)
                && TokenClaims.ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString(TokenClaims.TOKEN_TYPE))
                && jwt.getExpiresAt() != null
                && jwt.getIssuedAt() != null
                && jwt.getId() != null;
        if (!validShape) {
            return false;
        }
        try {
            UUID.fromString(jwt.getSubject());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
