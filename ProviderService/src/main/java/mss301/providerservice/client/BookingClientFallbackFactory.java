package mss301.providerservice.client;

import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.client.dto.BusyCaregiverClientRequest;
import mss301.providerservice.client.dto.BusyCaregiverClientResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BookingClientFallbackFactory implements FallbackFactory<BookingClient> {

    @Override
    public BookingClient create(Throwable cause) {
        return new BookingClient() {
            @Override
            public RemoteApiResponse<BusyCaregiverClientResponse> findBusyCaregivers(BusyCaregiverClientRequest request) {
                // Lỗi đã dịch (4702, token 4700) giữ nguyên; lỗi mạng / timeout / mạch mở → 503.
                if (cause instanceof AppException appException) {
                    throw appException;
                }
                log.warn("BookingService unavailable ({})", cause.getClass().getSimpleName());
                throw new AppException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE);
            }
        };
    }
}
