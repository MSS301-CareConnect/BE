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

@Component
@Slf4j
public class BookingClientFallbackFactory implements FallbackFactory<BookingClient> {

    // Fallback khi gọi BookingService lỗi: ném AppException tương ứng.
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
