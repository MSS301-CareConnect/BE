package mss301.identityservice.util;

import java.util.Locale;

/**
 * Chuẩn hoá email về một dạng duy nhất (trim + chữ thường) — "1 email = 1 tài khoản" không bị phá
 * bởi khác biệt hoa/thường. DB cũng có CHECK {@code email = lower(btrim(email))}.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
