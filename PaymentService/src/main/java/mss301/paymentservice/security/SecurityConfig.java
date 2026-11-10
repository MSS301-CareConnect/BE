package mss301.paymentservice.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityConfig {
    // Callback cổng thanh toán: xác thực bằng chữ ký HMAC / Stripe-Signature thay cho JWT (rule #3).
    static String[] PUBLIC_PATHS = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health/**", "/actuator/health",
            "/api/v1/payments/vnpay/ipn", "/api/v1/payments/vnpay/return",
            "/api/v1/payments/stripe/webhook", "/api/v1/payments/stripe/return"
    };

    JwtDecoder jwtDecoder;
    JwtAuthorityConverter jwtAuthorityConverter;
    SecurityErrorHandler securityErrorHandler;
    DefaultBearerTokenResolver headerTokenResolver = new DefaultBearerTokenResolver();
    RequestMatcher publicEndpoints = new OrRequestMatcher(Arrays.stream(PUBLIC_PATHS)
            .map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(path))
            .toList());

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicEndpoints).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(this::resolveToken)
                        .authenticationEntryPoint(securityErrorHandler)
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthorityConverter)))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    // Web gửi JWT qua cookie HttpOnly access_token (giống Booking/Gateway); mobile/service gửi header Bearer.
    // Endpoint public không đọc token: cookie cũ/hết hạn không được chặn callback cổng thanh toán.
    private String resolveToken(HttpServletRequest request) {
        if (publicEndpoints.matches(request)) {
            return null;
        }
        String bearerToken = headerTokenResolver.resolve(request);
        String cookieToken = readAccessCookie(request);
        // Cookie và header mang hai token khác nhau → từ chối, không đoán client muốn dùng cái nào.
        if (bearerToken != null && cookieToken != null && !bearerToken.equals(cookieToken)) {
            throw new OAuth2AuthenticationException("Conflicting token transports");
        }
        return bearerToken == null ? cookieToken : bearerToken;
    }

    private static String readAccessCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (TokenClaims.ACCESS_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
