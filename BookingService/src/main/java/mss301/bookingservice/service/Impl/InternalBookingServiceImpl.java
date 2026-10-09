package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.dto.request.DepositPaidRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.CaregiverAccessResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingAction;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.BookingMapper;
import mss301.bookingservice.mapper.OccurrenceMapper;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.service.BookingLifecycleService;
import mss301.bookingservice.service.BookingReadService;
import mss301.bookingservice.service.InternalBookingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class InternalBookingServiceImpl implements InternalBookingService {

    static String PAYMENT_REFERENCE_PREFIX = "paymentReference=";

    BookingRepository bookingRepository;
    BookingOccurrenceRepository bookingOccurrenceRepository;
    BookingLifecycleService bookingLifecycleService;
    BookingReadService bookingReadService;
    BookingMapper bookingMapper;
    OccurrenceMapper occurrenceMapper;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverAccessResponse> checkCaregiverAccess(UUID caregiverUserId, UUID recipientId) {
        boolean allowed = bookingRepository.existsCaregiverAssignment(caregiverUserId, recipientId,
                BookingStatus.CAREGIVER_RECIPIENT_ACCESS, OccurrenceStatus.ACTIVE);
        CaregiverAccessResponse caregiverAccessResponse = bookingMapper.toCaregiverAccessResponse(caregiverUserId,
                recipientId, allowed);
        return APIResponse.success("Kiểm tra phân công thành công", caregiverAccessResponse);
    }

    // Không gọi mạng → toàn bộ trong một transaction (khóa dòng → state machine → lịch sử).
    @Override
    @Transactional
    public APIResponse<BookingDetailResponse> markDepositPaid(UUID id, DepositPaidRequest request) {
        Booking booking = bookingRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            BookingDetailResponse confirmedResponse = bookingReadService.toDetailResponse(booking, false);
            return APIResponse.success("Booking đã được xác nhận đặt cọc trước đó", confirmedResponse);
        }
        BookingAction.PAY_DEPOSIT.apply(booking.getStatus());
        // Quá hạn cọc nhưng scheduler chưa kịp chuyển EXPIRED → không xác nhận (TODO(Payment): hoàn tiền nếu đã thu).
        if (!clock.instant().isBefore(booking.getDepositDueAt())) {
            throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
        }
        String paymentReference = request == null ? null : request.getPaymentReference();
        String reason = paymentReference == null || paymentReference.isBlank()
                ? null
                : PAYMENT_REFERENCE_PREFIX + paymentReference.strip();
        bookingLifecycleService.apply(booking, BookingAction.PAY_DEPOSIT, null, reason);
        log.info("Booking {} deposit confirmed", id);
        BookingDetailResponse bookingDetailResponse = bookingReadService.toDetailResponse(booking, false);
        return APIResponse.success("Xác nhận đặt cọc thành công", bookingDetailResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<BookingDetailResponse> get(UUID id) {
        Booking booking = bookingRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        BookingDetailResponse bookingDetailResponse = bookingReadService.toDetailResponse(booking, false);
        return APIResponse.success("Lấy booking thành công", bookingDetailResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverOccurrenceResponse> getOccurrence(UUID occurrenceId) {
        BookingOccurrence occurrence = bookingOccurrenceRepository.findWithBooking(occurrenceId)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        CaregiverOccurrenceResponse occurrenceResponse = occurrenceMapper.toCaregiverDetailResponse(occurrence);
        return APIResponse.success("Lấy buổi chăm sóc thành công", occurrenceResponse);
    }
}
