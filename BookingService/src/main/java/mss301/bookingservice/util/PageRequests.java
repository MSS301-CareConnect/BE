package mss301.bookingservice.util;

import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

// Phân trang chung cho danh sách booking: page ≥ 0, 1 ≤ size ≤ 100, mới tạo trước.
public final class PageRequests {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String CREATED_AT = "createdAt";

    private PageRequests() {
    }

    public static PageRequest newestFirst(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, CREATED_AT));
    }
}
