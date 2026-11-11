package mss301.aiservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.util.UUID;

public class ReadOnlyCookieCsrfTokenRepository implements CsrfTokenRepository {
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private static final String CSRF_PARAMETER = "_csrf";

    // Sinh CSRF token mới (chỉ dùng nội bộ, cookie do IdentityService phát).
    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return new DefaultCsrfToken(CSRF_HEADER, CSRF_PARAMETER, UUID.randomUUID().toString());
    }

    // Không ghi cookie CSRF (IdentityService là nơi phát cookie).
    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {

    }

    // Đọc CSRF token từ cookie XSRF-TOKEN.
    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        String tokenValue = CookieUtils.read(request, CookieUtils.CSRF);

        if (tokenValue == null || tokenValue.isBlank()) {
            return null;
        }

        return new DefaultCsrfToken(CSRF_HEADER, CSRF_PARAMETER, tokenValue);
    }
}
