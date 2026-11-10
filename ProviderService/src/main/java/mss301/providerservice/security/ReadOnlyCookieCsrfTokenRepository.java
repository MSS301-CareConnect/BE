package mss301.providerservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.util.UUID;

/**
 * CSRF double-submit với cookie XSRF-TOKEN do IdentityService phát (Path=/, cùng host qua gateway): so header
 * X-XSRF-TOKEN với cookie. Không bao giờ ghi cookie — nếu service này tự phát token, cookie của Identity bị ghi đè
 * và token FE đang giữ mất hiệu lực.
 */
public class ReadOnlyCookieCsrfTokenRepository implements CsrfTokenRepository {

    public static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String CSRF_PARAMETER = "_csrf";

    // Chỉ dùng khi request không có cookie: token ngẫu nhiên này không khớp header nào → CSRF thất bại (403).
    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return new DefaultCsrfToken(CSRF_HEADER, CSRF_PARAMETER, UUID.randomUUID().toString());
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        // Cố ý không ghi cookie (xem javadoc).
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        String value = CookieUtils.read(request, CookieUtils.CSRF);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return new DefaultCsrfToken(CSRF_HEADER, CSRF_PARAMETER, value);
    }
}
