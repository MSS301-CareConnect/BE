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

    public static final String REFRESH_PATH = "/api/v1/auth/refresh";
    public static final String ROOT_PATH = "/";

    // Đặt access + refresh HttpOnly và xoay CSRF token mới; trả CSRF để client cập nhật header.
    public String issue(HttpServletResponse response, String accessToken, String refreshToken) {
        set(response, ACCESS, accessToken, identityProperties.getAccessTtl(), ROOT_PATH);
        set(response, REFRESH, refreshToken, identityProperties.getRefreshTtl(), REFRESH_PATH);
        String csrfToken = UUID.randomUUID().toString();
        set(response, CSRF, csrfToken, SESSION_COOKIE, ROOT_PATH);
        return csrfToken;
    }

    public void clear(HttpServletResponse response) {
        set(response, ACCESS, "", Duration.ZERO, ROOT_PATH);
        set(response, REFRESH, "", Duration.ZERO, REFRESH_PATH);
        set(response, REFRESH, "", Duration.ZERO, ROOT_PATH); // Xóa cả cookie cũ nếu có path=/
        set(response, CSRF, "", Duration.ZERO, ROOT_PATH);
    }

    private void set(HttpServletResponse response, String name, String value, Duration maxAge, String path) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(identityProperties.isCookieSecure())
                .sameSite(identityProperties.getCookieSameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
