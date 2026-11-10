package mss301.carecatalogservice.util;

import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

// Phân trang chung: page ≥ 0, 1 ≤ size ≤ 100 (CAT-02), sai → 400 INVALID_PAGE.
public final class PageRequests {

    private static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    public static PageRequest of(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        return PageRequest.of(page, size, sort);
    }
}
