package mss301.bookingservice.client;

import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.dto.ProviderCenterResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class ProviderClientFallbackFactory implements FallbackFactory<ProviderClient> {

    // Fallback khi gọi ProviderService lỗi: ném AppException tương ứng.
    @Override
    public ProviderClient create(Throwable cause) {
        return new ProviderClient() {
            @Override
            public RemoteApiResponse<ProviderCenterResponse> getCenter(UUID centerId) {
                throw toAppException(cause);
            }
        };
    }

    // Giữ nguyên AppException, lỗi khác đổi thành dịch vụ không khả dụng.
    private static AppException toAppException(Throwable cause) {
        if (cause instanceof AppException appException) {
            return appException;
        }
        log.warn("ProviderService call failed ({})", cause.getClass().getSimpleName());
        return new AppException(ErrorCode.PROVIDER_UNAVAILABLE);
    }
}
