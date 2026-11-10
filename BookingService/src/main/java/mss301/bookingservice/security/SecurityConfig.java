package mss301.bookingservice.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.List;
import java.util.Set;

/**
 * Resource Server: tự xác minh JWT (không tin header X-User-* của gateway). CORS tắt — trình duyệt đi qua gateway
 * (gateway xử lý CORS và bỏ header Origin khi chuyển tiếp).
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityConfig {

    static String[] PUBLIC_ENDPOINTS = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health/**"
    };

    // Endpoint public không đọc token — cookie cũ/hỏng của trình duyệt không được chặn Swagger/health.
    static List<String> PUBLIC_PREFIXES = List.of("/v3/api-docs", "/swagger-ui", "/actuator/health");
    static Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    JwtDecoder jwtDecoder;
    JwtAuthorityConverter jwtAuthorityConverter;
    SecurityErrorHandler securityErrorHandler;
    DefaultBearerTokenResolver headerTokenResolver = new DefaultBearerTokenResolver();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(new ReadOnlyCookieCsrfTokenRepository())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler())
                        .sessionAuthenticationStrategy((authentication, request, response) -> {
                        })
                        .withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                            @Override
                            public <O extends CsrfFilter> O postProcess(O filter) {
                                // Resource Server tự miễn CSRF cho bearer → dùng chính sách có xét cookie như Identity.
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
                                .jwtAuthenticationConverter(jwtAuthorityConverter)))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        // FE gửi nguyên giá trị token (lấy từ /auth/csrf của Identity) trong header X-XSRF-TOKEN.
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    // Chỉ request ghi mang cookie access_token mới cần CSRF (kể cả có thêm bearer); bearer thuần (mobile) được miễn.
    private boolean requiresCsrf(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        return CookieUtils.read(request, CookieUtils.ACCESS) != null;
    }

    private String resolveToken(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String publicPrefix : PUBLIC_PREFIXES) {
            if (path.startsWith(publicPrefix)) {
                return null;
            }
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
