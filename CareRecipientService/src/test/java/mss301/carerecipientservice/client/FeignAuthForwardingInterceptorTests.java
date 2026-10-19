package mss301.carerecipientservice.client;

import feign.RequestTemplate;
import jakarta.servlet.http.Cookie;
import mss301.carerecipientservice.config.RequestIdFilter;
import mss301.carerecipientservice.security.CookieUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeignAuthForwardingInterceptorTests {

    private final FeignAuthForwardingInterceptor interceptor = new FeignAuthForwardingInterceptor();

    @BeforeEach
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void whenSecurityContextHasJwt_thenSetsBearerTokenFromJwt() {
        Jwt jwt = new Jwt("jwt-token-value-123", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"), Map.of("sub", "user-123"));
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertThat(authHeaders).containsExactly("Bearer jwt-token-value-123");
    }

    @Test
    void whenSecurityContextEmpty_andRequestHasAuthorizationHeader_thenForwardsAuthHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer header-token-456");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertThat(authHeaders).containsExactly("Bearer header-token-456");
    }

    @Test
    void whenSecurityContextEmpty_andRequestHasNoHeader_butHasAccessTokenCookie_thenSetsBearerCookieToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieUtils.ACCESS, "cookie-token-789"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertThat(authHeaders).containsExactly("Bearer cookie-token-789");
    }

    @Test
    void whenSecurityContextHasNonJwtAuth_andRequestHasCookie_thenFallsBackToRequestCookie() {
        UsernamePasswordAuthenticationToken nonJwt = new UsernamePasswordAuthenticationToken("user", "pass");
        SecurityContextHolder.getContext().setAuthentication(nonJwt);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieUtils.ACCESS, "cookie-token-fallback"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertThat(authHeaders).containsExactly("Bearer cookie-token-fallback");
    }

    @Test
    void whenMdcHasRequestId_thenSetsXRequestIdHeader() {
        MDC.put(RequestIdFilter.MDC_KEY, "test-req-id-999");

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        Collection<String> reqIdHeaders = template.headers().get(RequestIdFilter.HEADER);
        assertThat(reqIdHeaders).containsExactly("test-req-id-999");
    }

    @Test
    void whenUnauthenticatedAndNoRequest_thenDoesNotThrowAndSetsNoAuthHeader() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get("Authorization")).isNull();
        assertThat(template.headers().get(RequestIdFilter.HEADER)).isNull();
    }
}
