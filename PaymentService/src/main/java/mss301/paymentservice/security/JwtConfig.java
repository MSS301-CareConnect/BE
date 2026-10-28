package mss301.paymentservice.security;

import mss301.paymentservice.config.PaymentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.util.UUID;

@Configuration
public class JwtConfig {
    @Bean
    JwtDecoder jwtDecoder(PaymentProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwt().getJwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> claims = jwt -> validClaims(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token claims", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getJwt().getIssuer()), claims));
        return decoder;
    }

    private boolean validClaims(Jwt jwt) {
        if (jwt.getAudience() == null || !jwt.getAudience().contains(TokenClaims.API_AUDIENCE)
                || !TokenClaims.ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString(TokenClaims.TOKEN_TYPE))
                || jwt.getExpiresAt() == null || jwt.getIssuedAt() == null || jwt.getId() == null) return false;
        try {
            UUID.fromString(jwt.getSubject());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
