package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.Booking;
import org.springframework.data.domain.Page;

/**
 * Dựng response đọc dùng chung cho customer / trung tâm / admin / nội bộ. includeCustomer = màn hình trung tâm / admin:
 * gọi Identity batch (một lần cho cả trang), Identity lỗi → thiếu thông tin khách, không lỗi. Không gọi trong transaction.
 */
public interface BookingReadService {
    BookingDetailResponse toDetailResponse(Booking booking, boolean includeCustomer);

    PageResponse<BookingSummaryResponse> toPageResponse(Page<Booking> bookings, boolean includeCustomer);
}
