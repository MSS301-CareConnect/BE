package mss301.carerecipientservice.client;

import mss301.carerecipientservice.client.dto.RecipientActivityResponse;
import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.client.dto.BookingApiResponse;
import mss301.carerecipientservice.client.dto.CaregiverAccessResponse;
import mss301.carerecipientservice.client.dto.NotificationCreateRequest;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fallback khi BookingService lỗi / timeout / circuit mở: kiểm phân công dùng để CẤP QUYỀN đọc hồ sơ người bệnh →
 * fail-closed, ném 503 (không trả "không được phép" để caregiver biết là lỗi tạm thời, không phải bị gỡ phân công).
 */
@Component
@Slf4j
public class BookingClientFallbackFactory implements FallbackFactory<BookingClient> {

    @Override
    public BookingClient create(Throwable cause) {
        return new BookingClient() {
            @Override
            public BookingApiResponse<CaregiverAccessResponse> checkCaregiverAccess(UUID caregiverUserId, UUID recipientId) {
                if (cause instanceof AppException appException) {
                    throw appException;
                }
                log.warn("BookingService call failed ({})", cause.getClass().getSimpleName());
                throw new AppException(ErrorCode.BOOKING_UNAVAILABLE);
            }

            @Override
            public BookingApiResponse<RecipientActivityResponse> getRecipientActivity(UUID recipientId) {
                if (cause instanceof AppException appException) {
                    throw appException;
                }
                log.warn("BookingService recipient activity failed ({})", cause.getClass().getSimpleName());
                throw new AppException(ErrorCode.BOOKING_UNAVAILABLE);
            }

            @Override
            public BookingApiResponse<Object> createNotification(NotificationCreateRequest request) {
                log.warn("BookingService notification failed ({})", cause.getClass().getSimpleName());
                throw new AppException(ErrorCode.BOOKING_UNAVAILABLE);
            }
        };
    }
}
