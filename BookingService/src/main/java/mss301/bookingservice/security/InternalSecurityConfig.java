package mss301.bookingservice.security;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.config.BookingProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Chain riêng cho /internal/** (service-to-service), xét TRƯỚC chain chính của {@link SecurityConfig}: không JWT,
 * không cookie → không CSRF/CORS; chỉ chấp nhận header X-Internal-Secret. Chain chính giữ nguyên.
 */
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalSecurityConfig {

    public static final String INTERNAL_PATH = "/internal/**";

    BookingProperties bookingProperties;
    SecurityErrorHandler securityErrorHandler;

    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(INTERNAL_PATH)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilterBefore(new InternalSecretFilter(bookingProperties.getInternalSecret()),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasAuthority(InternalSecretFilter.INTERNAL_SERVICE_AUTHORITY))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
