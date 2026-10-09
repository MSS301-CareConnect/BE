package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.BookingStatus;

import java.time.LocalDate;
import java.util.UUID;

// ADMIN xem booking toàn hệ thống (chỉ đọc).
public interface AdminBookingService {
    APIResponse<PageResponse<BookingSummaryResponse>> list(BookingStatus status, UUID centerId, UUID customerId,
                                                           UUID recipientId, LocalDate from, LocalDate to,
                                                           int page, int size);

    APIResponse<BookingDetailResponse> get(UUID id);
}
