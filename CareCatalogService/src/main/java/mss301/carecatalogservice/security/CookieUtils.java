package mss301.carecatalogservice.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public final class CookieUtils {
    public static final String ACCESS = "access_token";
    public static final String REFRESH = "refresh_token";
    public static final String CSRF = "XSRF-TOKEN";

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private CookieUtils() {
    }

    // Đọc giá trị cookie theo tên.
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
