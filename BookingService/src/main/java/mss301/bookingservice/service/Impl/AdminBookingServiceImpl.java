package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.BookingSpecifications;
import mss301.bookingservice.service.AdminBookingService;
import mss301.bookingservice.service.BookingReadService;
import mss301.bookingservice.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

// Không @Transactional: response gọi Identity batch (mạng) để hiển thị khách hàng.
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminBookingServiceImpl implements AdminBookingService {

    BookingRepository bookingRepository;
    BookingReadService bookingReadService;

    @Override
    public APIResponse<PageResponse<BookingSummaryResponse>> list(BookingStatus status, UUID centerId, UUID customerId,
                                                                  UUID recipientId, LocalDate from, LocalDate to,
                                                                  int page, int size) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new AppException(ErrorCode.INVALID_DATE_FILTER);
        }
        Page<Booking> bookings = bookingRepository.findAll(
                BookingSpecifications.filter(customerId, centerId, recipientId, null, status, from, to),
                PageRequests.newestFirst(page, size));
        PageResponse<BookingSummaryResponse> pageResponse = bookingReadService.toPageResponse(bookings, true);
        return APIResponse.success("Lấy danh sách booking thành công", pageResponse);
    }

    @Override
    public APIResponse<BookingDetailResponse> get(UUID id) {
        Booking booking = bookingRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        BookingDetailResponse bookingDetailResponse = bookingReadService.toDetailResponse(booking, true);
        return APIResponse.success("Lấy chi tiết booking thành công", bookingDetailResponse);
    }
}
