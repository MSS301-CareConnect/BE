package mss301.identityservice.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import mss301.identityservice.config.IdentityProperties;
import mss301.identityservice.enums.TokenType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Configuration
public class JwtConfig {

    private static final int MIN_RSA_KEY_BITS = 2048;

    @Bean
    public RSAKey signingKey(IdentityProperties identityProperties, ResourceLoader resourceLoader) throws IOException {
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        try (InputStream stream = resourceLoader.getResource(identityProperties.getPrivateKey()).getInputStream()) {
            privateKey = RsaKeyConverters.pkcs8().convert(stream);
        }
        try (InputStream stream = resourceLoader.getResource(identityProperties.getPublicKey()).getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(stream);
        }
        // Fail khi khởi động nếu cặp khóa sai/yếu — không chạy với khóa không khớp.
        if (privateKey == null || publicKey == null
                || publicKey.getModulus().bitLength() < MIN_RSA_KEY_BITS
                || !privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalArgumentException("A matching RSA key pair of at least 2048 bits is required");
        }
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(identityProperties.getKeyId())
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    // Decoder dùng chung (cả access lẫn refresh): RS256 + iss + aud + exp/iat + jti + sub UUID + authz_version.
    @Bean
    public NimbusJwtDecoder identityJwtDecoder(RSAKey signingKey, IdentityProperties identityProperties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> hasRequiredClaims(jwt, identityProperties)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token claims", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(identityProperties.getIssuer()), requiredClaims));
        return decoder;
    }

    // Decoder của Resource Server: chỉ nhận access token — refresh token không bao giờ dùng để gọi API.
    @Bean
    @Primary
    public JwtDecoder jwtDecoder(NimbusJwtDecoder identityJwtDecoder) {
        return token -> {
            Jwt jwt = identityJwtDecoder.decode(token);
            if (!TokenType.ACCESS.getValue().equals(jwt.getClaimAsString(TokenClaims.TOKEN_TYPE))) {
                throw new BadJwtException("Access token required");
            }
            return jwt;
        };
    }

    private static boolean hasRequiredClaims(Jwt jwt, IdentityProperties identityProperties) {
        boolean valid = jwt.getAudience().contains(identityProperties.getAudience())
                && jwt.getExpiresAt() != null
                && jwt.getIssuedAt() != null
                && jwt.getId() != null
                && jwt.getClaim(TokenClaims.AUTHZ_VERSION) instanceof Number;
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
