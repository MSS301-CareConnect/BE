package mss301.providerservice.util;

import java.util.Locale;
import java.util.Set;

/**
 * Mã loại ca. Cột {@code shift_type} vẫn là chuỗi (tương thích mobile / dữ liệu cũ) nên không dùng enum JPA:
 * ca mới chỉ nhận {@link #CODES}; DAILY / WEEKLY của dữ liệu cũ vẫn đọc được và được giữ nguyên khi sửa ca mà không
 * đổi loại.
 */
public final class ShiftTypes {

    public static final Set<String> CODES = Set.of("MORNING", "AFTERNOON", "EVENING", "NIGHT", "FULL_DAY", "CUSTOM");

    private ShiftTypes() {
    }

    public static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isKnown(String normalized) {
        return normalized != null && CODES.contains(normalized);
    }
}
