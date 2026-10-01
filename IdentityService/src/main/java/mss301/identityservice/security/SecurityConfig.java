package mss301.identityservice.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.config.IdentityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityConfig {

    // Luồng tự xử lý token của chính nó — cookie cũ/hỏng không được chặn login/refresh/logout/đăng ký.
    static String[] TOKEN_FLOW_ENDPOINTS = {
            "/auth/login", "/auth/refresh", "/auth/logout", "/auth/csrf",
            "/auth/mobile/login", "/auth/mobile/refresh", "/auth/mobile/logout",
            "/users/register", "/users/register/verify", "/users/register/resend-otp",
            "/users/forgot-password", "/users/reset-password"
    };

    static String[] PUBLIC_ENDPOINTS = Stream.concat(Stream.of(TOKEN_FLOW_ENDPOINTS), Stream.of(
            "/.well-known/jwks.json",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/actuator/health/**"
    )).toArray(String[]::new);

    static Set<String> TOKEN_FLOW_PATHS = Set.of(TOKEN_FLOW_ENDPOINTS);
    static Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    public static final String MOBILE_AUTH_PREFIX = "/auth/mobile/";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    IdentityProperties identityProperties;
    JwtDecoder jwtDecoder;
    IdentityAuthenticationConverter identityAuthenticationConverter;
    SecurityErrorHandler securityErrorHandler;
    DefaultBearerTokenResolver headerTokenResolver = new DefaultBearerTokenResolver();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler())
                        // JWT được xác thực ở MỖI request → chỉ rotate CSRF trong login/refresh thật, không ở mỗi lần đọc.
                        .sessionAuthenticationStrategy((authentication, request, response) -> {
                        })
                        .withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                            @Override
                            public <O extends CsrfFilter> O postProcess(O filter) {
                                // Resource Server tự thêm miễn CSRF cho bearer → khôi phục chính sách có xét cookie.
                                filter.setRequireCsrfProtectionMatcher(SecurityConfig.this::requiresCsrf);
                                return filter;
                            }
                        }))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                // Failure handler mặc định ném lại lỗi dependency vào /error → bị che thành 401.
                                filter.setAuthenticationFailureHandler(securityErrorHandler);
                                return filter;
                            }
                        })
                        .bearerTokenResolver(this::resolveToken)
                        .authenticationEntryPoint(securityErrorHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(identityAuthenticationConverter)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(identityProperties.getAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, CSRF_HEADER));
        cors.setAllowCredentials(true);
        cors.validateAllowCredentials();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        repository.setCookieCustomizer(cookie -> cookie
                .secure(identityProperties.isCookieSecure())
                .sameSite(identityProperties.getCookieSameSite())
                .path("/"));
        return repository;
    }

    private CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        // Token trả qua /auth/csrf; JS gửi nguyên giá trị trong header X-XSRF-TOKEN.
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    // Request mang cookie xác thực LUÔN cần CSRF (kể cả có thêm bearer); bearer thuần và /auth/mobile/* được miễn.
    private boolean requiresCsrf(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        if (CookieUtils.read(request, CookieUtils.ACCESS) != null || CookieUtils.read(request, CookieUtils.REFRESH) != null) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean hasBearer = header != null && header.startsWith(TokenClaims.BEARER_PREFIX);
        return !request.getRequestURI().startsWith(MOBILE_AUTH_PREFIX) && !hasBearer;
    }

    private String resolveToken(HttpServletRequest request) {
        if (TOKEN_FLOW_PATHS.contains(request.getRequestURI())) {
            return null;
        }
        String bearerToken = headerTokenResolver.resolve(request);
        String cookieToken = CookieUtils.read(request, CookieUtils.ACCESS);
        // Cookie và header mang hai token khác nhau → từ chối, không đoán client muốn dùng cái nào.
        if (bearerToken != null && cookieToken != null && !bearerToken.equals(cookieToken)) {
            throw new OAuth2AuthenticationException("Conflicting token transports");
        }
        return bearerToken == null ? cookieToken : bearerToken;
    }
}
