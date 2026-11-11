package mss301.identityservice.util;

import java.util.Locale;

public final class EmailNormalizer {

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private EmailNormalizer() {
    }

    // Chuẩn hóa email: bỏ khoảng trắng, chữ thường.
    public static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
