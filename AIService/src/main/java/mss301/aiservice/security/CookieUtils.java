package mss301.aiservice.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public final class CookieUtils {
    public static final String ACCESS = "access_token";

    public static final String CSRF = "XSRF-TOKEN";

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private CookieUtils() {
    }

    // Đọc giá trị cookie theo tên.
    public static String read(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
