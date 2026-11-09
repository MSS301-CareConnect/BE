package mss301.aiservice.security;

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

import java.util.List;
import java.util.Set;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityConfig {
    static String[] PUBLIC_ENDPOINTS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health/**"
    };

    static List<String> TOKEN_IGNORED_PREFIXES = List.of("/v3/api-docs", "/swagger-ui", "/actuator/health");
    static Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    JwtDecoder jwtDecoder;
    JwtAuthorityConverter jwtAuthorityConverter;
    SecurityErrorHandler securityErrorHandler;
    DefaultBearerTokenResolver headerTokenResolver = new DefaultBearerTokenResolver();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .cors(AbstractHttpConfigurer::disable)

                .csrf(csrf -> csrf
                        .csrfTokenRepository(new ReadOnlyCookieCsrfTokenRepository())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler())
                        .sessionAuthenticationStrategy((authentication, request, response) -> {})
                        .withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                                    @Override
                                    public <O extends CsrfFilter> O postProcess(O filter) {
                                        filter.setRequireCsrfProtectionMatcher(SecurityConfig.this::requiresCsrf);
                                        return filter;
                                    }
                                }
                        )
                )

                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/ai/chat")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/chat/quota")
                        .permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS)
                        .permitAll()
                        .anyRequest()
                        .authenticated()
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))

                .oauth2ResourceServer(resourceServer ->
                        resourceServer.withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                                            @Override public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                                filter.setAuthenticationFailureHandler(securityErrorHandler);
                                                return filter;
                                            }
                                        }
                                )
                                .bearerTokenResolver(this::resolveToken)
                                .authenticationEntryPoint(securityErrorHandler)
                                .jwt(jwt -> jwt.decoder(jwtDecoder)
                                        .jwtAuthenticationConverter(jwtAuthorityConverter))
                )

                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }

    private CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    private boolean requiresCsrf(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }

        return CookieUtils.read(request, CookieUtils.ACCESS) != null;
    }

    private String resolveToken(HttpServletRequest request) {
        String path = request.getRequestURI();

        for (String ignoredPrefix : TOKEN_IGNORED_PREFIXES) {
            if (path.startsWith(ignoredPrefix)) {
                return null;
            }
        }

        String bearerToken = headerTokenResolver.resolve(request);
        String cookieToken = CookieUtils.read(request, CookieUtils.ACCESS);

        if (bearerToken != null && cookieToken != null && !bearerToken.equals(cookieToken)) {
            throw new OAuth2AuthenticationException("Conflicting token transports");
        }

        return bearerToken == null
                ? cookieToken
                : bearerToken;
    }
}
