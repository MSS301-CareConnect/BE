package mss301.bookingservice.util;

import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Map;

/**
 * BK-06: sort danh sách booking dạng "field,asc|desc" — chỉ field trong whitelist (không cho sort theo cột tùy ý như
 * customerId, tránh dò dữ liệu / truy vấn không có index). Field khác → 400 INVALID_SORT.
 */
public final class BookingSorts {

    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "createdAt", "createdAt",
            "startDate", "startDate");
    private static final String FIELD_SEPARATOR = ",";
    private static final String DEFAULT_SORT = "createdAt,desc";

    private BookingSorts() {
    }

    public static Sort parse(String sort) {
        String value = sort == null || sort.isBlank() ? DEFAULT_SORT : sort.strip();
        String[] parts = value.split(FIELD_SEPARATOR, -1);
        if (parts.length > 2 || !SORTABLE_FIELDS.containsKey(parts[0].strip())) {
            throw new AppException(ErrorCode.INVALID_SORT);
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            try {
                direction = Sort.Direction.valueOf(parts[1].strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.INVALID_SORT);
            }
        }
        // Sắp phụ theo id để phân trang ổn định khi trùng giá trị.
        return Sort.by(direction, SORTABLE_FIELDS.get(parts[0].strip())).and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
