package mss301.providerservice.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Tìm kiếm không dấu: "an tam" khớp "An Tâm". Phía Java gập từ khóa bằng {@link #fold}; phía SQL gập cột bằng
 * {@code translate(lower(col), ACCENTED, PLAIN)} với đúng hai bảng ký tự này — không cần extension unaccent.
 */
public final class VietnameseText {

    /** Mọi chữ thường có dấu của tiếng Việt (dạng dựng sẵn NFC) và chữ đ. */
    public static final String ACCENTED =
            "àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ";

    /** Chữ không dấu tương ứng từng vị trí của {@link #ACCENTED}. */
    public static final String PLAIN = fold(ACCENTED);

    private VietnameseText() {
    }

    /** Chữ thường, bỏ dấu, đ → d. */
    public static String fold(String value) {
        String lower = Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd');
    }
}
