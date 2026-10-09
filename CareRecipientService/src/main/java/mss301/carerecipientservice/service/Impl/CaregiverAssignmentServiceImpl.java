package mss301.carerecipientservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.client.BookingClient;
import mss301.carerecipientservice.client.dto.BookingApiResponse;
import mss301.carerecipientservice.client.dto.CaregiverAccessResponse;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.service.CaregiverAssignmentService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CaregiverAssignmentServiceImpl implements CaregiverAssignmentService {

    BookingClient bookingClient;

    @Override
    public boolean isAssigned(UUID recipientId, UUID caregiverUserId) {
        BookingApiResponse<CaregiverAccessResponse> response = bookingClient.checkCaregiverAccess(caregiverUserId, recipientId);
        // Phản hồi rỗng = lỗi tích hợp → không đoán (fail-closed bằng 503, không phải "không được phép").
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.BOOKING_UNAVAILABLE);
        }
        return response.getResult().isAllowed();
    }
}
