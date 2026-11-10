package mss301.providerservice.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Cookie do IdentityService đặt (HttpOnly, Path=/) và trình duyệt gửi kèm qua gateway. Service này chỉ ĐỌC,
 * không bao giờ set/xóa cookie.
 */
public final class CookieUtils {
    public static final String ACCESS = "access_token";
    public static final String REFRESH = "refresh_token";
    public static final String CSRF = "XSRF-TOKEN";

    private CookieUtils() {
    }

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
}
