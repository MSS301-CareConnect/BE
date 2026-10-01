package mss301.identityservice.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.config.IdentityProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CookieUtils {
    public static final String ACCESS = "access_token";
    public static final String REFRESH = "refresh_token";
    public static final String CSRF = "XSRF-TOKEN";

    // maxAge âm = session cookie (CSRF sống theo phiên trình duyệt).
    static Duration SESSION_COOKIE = Duration.ofSeconds(-1);

    IdentityProperties identityProperties;

    public static String read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    // Đặt access + refresh HttpOnly và xoay CSRF token mới; trả CSRF để client cập nhật header.
    public String issue(HttpServletResponse response, String accessToken, String refreshToken) {
        set(response, ACCESS, accessToken, identityProperties.getAccessTtl());
        set(response, REFRESH, refreshToken, identityProperties.getRefreshTtl());
        String csrfToken = UUID.randomUUID().toString();
        set(response, CSRF, csrfToken, SESSION_COOKIE);
        return csrfToken;
    }

    public void clear(HttpServletResponse response) {
        set(response, ACCESS, "", Duration.ZERO);
        set(response, REFRESH, "", Duration.ZERO);
        set(response, CSRF, "", Duration.ZERO);
    }

    private void set(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(identityProperties.isCookieSecure())
                .sameSite(identityProperties.getCookieSameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
