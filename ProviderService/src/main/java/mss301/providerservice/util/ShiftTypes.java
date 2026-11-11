package mss301.providerservice.util;

import java.util.Locale;
import java.util.Set;

public final class ShiftTypes {

    public static final Set<String> CODES = Set.of("MORNING", "AFTERNOON", "EVENING", "NIGHT", "FULL_DAY", "CUSTOM");

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private ShiftTypes() {
    }

    // Chuẩn hóa mã loại ca: trim, chữ hoa.
    public static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    // Kiểm tra mã loại ca có trong danh sách.
    public static boolean isKnown(String normalized) {
        return normalized != null && CODES.contains(normalized);
    }
}
