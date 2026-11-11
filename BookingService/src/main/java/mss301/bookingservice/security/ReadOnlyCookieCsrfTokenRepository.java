package mss301.bookingservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.util.UUID;

public class ReadOnlyCookieCsrfTokenRepository implements CsrfTokenRepository {

    public static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String CSRF_PARAMETER = "_csrf";

    // Chỉ dùng khi request không có cookie: token ngẫu nhiên này không khớp header nào → CSRF thất bại (403).
    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return new DefaultCsrfToken(CSRF_HEADER, CSRF_PARAMETER, UUID.randomUUID().toString());
    }

    // Không ghi cookie CSRF (IdentityService là nơi phát cookie).
    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        // Cố ý không ghi cookie (xem javadoc).
    }

    // Đọc CSRF token từ cookie XSRF-TOKEN.
    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        String value = CookieUtils.read(request, CookieUtils.CSRF);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return new DefaultCsrfToken(CSRF_HEADER, CSRF_PARAMETER, value);
    }
}
