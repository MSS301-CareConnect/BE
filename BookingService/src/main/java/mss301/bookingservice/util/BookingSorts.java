package mss301.bookingservice.util;

import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class BookingSorts {

    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "createdAt", "createdAt",
            "startDate", "startDate");
    // Hạn đang chạy là biểu thức theo trạng thái — BookingSpecifications.orderByDeadline sắp, không qua Sort.
    private static final String DEADLINE_FIELD = "deadlineAt";
    private static final String FIELD_SEPARATOR = ",";
    private static final String DEFAULT_SORT = "createdAt,desc";

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private BookingSorts() {
    }

    // "deadlineAt,…" → Sort.unsorted() (sắp bằng orderByDeadline, xem deadlineDirection).
    public static Sort parse(String sort) {
        String[] parts = split(sort);
        Sort.Direction direction = parseDirection(parts);
        if (DEADLINE_FIELD.equals(parts[0].strip())) {
            return Sort.unsorted();
        }
        // Sắp phụ theo id để phân trang ổn định khi trùng giá trị.
        return Sort.by(direction, SORTABLE_FIELDS.get(parts[0].strip())).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    // Hướng sắp theo hạn khi sort là "deadlineAt[,asc|desc]"; rỗng khi sắp theo cột khác.
    public static Optional<Sort.Direction> deadlineDirection(String sort) {
        String[] parts = split(sort);
        return DEADLINE_FIELD.equals(parts[0].strip()) ? Optional.of(parseDirection(parts)) : Optional.empty();
    }

    // Tách tham số sort "trường,hướng", trống thì dùng mặc định.
    private static String[] split(String sort) {
        String value = sort == null || sort.isBlank() ? DEFAULT_SORT : sort.strip();
        String[] parts = value.split(FIELD_SEPARATOR, -1);
        String field = parts[0].strip();
        if (parts.length > 2 || !(SORTABLE_FIELDS.containsKey(field) || DEADLINE_FIELD.equals(field))) {
            throw new AppException(ErrorCode.INVALID_SORT);
        }
        return parts;
    }

    // Đọc hướng sắp xếp, mặc định tăng dần.
    private static Sort.Direction parseDirection(String[] parts) {
        if (parts.length < 2) {
            return Sort.Direction.ASC;
        }
        try {
            return Sort.Direction.valueOf(parts[1].strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_SORT);
        }
    }
}
