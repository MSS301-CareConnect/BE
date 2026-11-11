package mss301.carecatalogservice.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;
import java.util.Set;

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

    // Ảnh minh họa dịch vụ chuẩn: CHỈ GET, không cần đăng nhập và không đọc token — cookie hết hạn của khách không được
    // làm vỡ ảnh. Đồng bộ với GatewayPaths.PUBLIC_GET_ENDPOINTS của ApiGateway.
    static RequestMatcher PUBLIC_GET_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/catalog-items/images/{mediaFileId}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/care-services/images/{mediaFileId}"));

    // Duyệt dịch vụ cho khách chưa đăng nhập (2026-11-09): cho phép ẩn danh nhưng VẪN đọc token nếu có, để admin /
    // manager giữ góc nhìn đầy đủ; khách thấy như CUSTOMER. Đồng bộ với GatewayPaths.OPTIONAL_AUTH_GET_ENDPOINTS.
    static RequestMatcher OPTIONAL_AUTH_GET_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/catalog-items"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/catalog-items/{id}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/center-services/center/{centerId}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/center-services/{id}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET,
                    "/api/v1/price-plans/center-offering/{centerOfferingId}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET,
                    "/api/v1/price-plans/center-service/{centerOfferingId}"));

    JwtDecoder jwtDecoder;
    JwtAuthorityConverter jwtAuthorityConverter;
    SecurityErrorHandler securityErrorHandler;
    DefaultBearerTokenResolver headerTokenResolver = new DefaultBearerTokenResolver();

    // Cấu hình bảo mật: endpoint public, CSRF, xác thực JWT, xử lý lỗi 401/403.
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
                        .requestMatchers(PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(OPTIONAL_AUTH_GET_ENDPOINTS).permitAll()
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

    // Cấu hình cách đọc CSRF token từ header của request.
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

    // Lấy access token từ header Authorization hoặc cookie access_token.
    private String resolveToken(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String publicPrefix : PUBLIC_PREFIXES) {
            if (path.startsWith(publicPrefix)) {
                return null;
            }
        }
        if (PUBLIC_GET_ENDPOINTS.matches(request)) {
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
