package mss301.apigateway.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.apigateway.config.GatewayPaths;
import mss301.apigateway.config.GatewayProperties;
import mss301.apigateway.filter.RequestIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Gateway: xác thực JWT sớm + CORS tập trung. Không CSRF ở gateway — IdentityService / CareRecipientService tự kiểm
 * CSRF (cookie XSRF-TOKEN + header X-XSRF-TOKEN được chuyển nguyên). Không session.
 */
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityConfig {

    static long CORS_MAX_AGE_SECONDS = Duration.ofHours(1).toSeconds();

    GatewayProperties gatewayProperties;
    JwtDecoder jwtDecoder;
    JwtAuthorityConverter jwtAuthorityConverter;
    SecurityErrorHandler securityErrorHandler;
    DefaultBearerTokenResolver headerTokenResolver = new DefaultBearerTokenResolver();
    RequestMatcher publicEndpoints = toMatcher(Stream.concat(
            Stream.of(GatewayPaths.IDENTITY_TOKEN_FLOW_ENDPOINTS),
            Stream.of(GatewayPaths.DOCUMENTATION_ENDPOINTS)).toArray(String[]::new));

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(publicEndpoints).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                // Failure handler mặc định ném lại lỗi dependency (JWKS) → bị che thành 401.
                                filter.setAuthenticationFailureHandler(securityErrorHandler);
                                return filter;
                            }
                        })
                        .bearerTokenResolver(this::resolveToken)
                        .authenticationEntryPoint(securityErrorHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthorityConverter)))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(gatewayProperties.getAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
                TokenClaims.CSRF_HEADER, RequestIdFilter.HEADER));
        cors.setExposedHeaders(List.of(RequestIdFilter.HEADER));
        cors.setAllowCredentials(true);
        cors.setMaxAge(CORS_MAX_AGE_SECONDS);
        cors.validateAllowCredentials();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    // Endpoint public không đọc token: cookie access_token cũ/hết hạn không được chặn login/refresh/logout/Swagger.
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

    private static RequestMatcher toMatcher(String[] patterns) {
        List<RequestMatcher> matchers = new ArrayList<>();
        for (String pattern : patterns) {
            matchers.add(PathPatternRequestMatcher.withDefaults().matcher(pattern));
        }
        return new OrRequestMatcher(matchers);
    }
}
