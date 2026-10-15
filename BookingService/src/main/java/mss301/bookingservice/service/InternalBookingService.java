package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.DepositPaidRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.CaregiverAccessResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;

import java.util.UUID;

// API nội bộ /internal/bookings/** (X-Internal-Secret, gateway chặn) cho CareRecipient, Payment và các service sau.
public interface InternalBookingService {
    // Luôn 200: allowed = caregiver đang được phân công cho booking còn hiệu lực (CONFIRMED / IN_PROGRESS) của người bệnh.
    APIResponse<CaregiverAccessResponse> checkCaregiverAccess(UUID caregiverUserId, UUID recipientId);

    // TODO(Payment): thay bằng event của Payment Service. Gọi lại khi đã CONFIRMED → 200, không đổi gì.
    APIResponse<BookingDetailResponse> markDepositPaid(UUID id, DepositPaidRequest request);

    APIResponse<BookingDetailResponse> get(UUID id);

    APIResponse<CaregiverOccurrenceResponse> getOccurrence(UUID occurrenceId);
}
