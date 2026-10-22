package mss301.bookingservice.client;

import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.dto.ProviderCenterResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fallback khi ProviderService lỗi / timeout / circuit mở: mọi lời gọi dùng để XÁC MINH trước khi ghi (trung tâm
 * đang hoạt động, caregiver hợp lệ) → không đoán, ném lại lỗi nghiệp vụ (404) hoặc 503.
 */
@Component
@Slf4j
public class ProviderClientFallbackFactory implements FallbackFactory<ProviderClient> {

    @Override
    public ProviderClient create(Throwable cause) {
        return new ProviderClient() {
            @Override
            public RemoteApiResponse<ProviderCenterResponse> getCenter(UUID centerId) {
                throw toAppException(cause);
            }
        };
    }

    private static AppException toAppException(Throwable cause) {
        if (cause instanceof AppException appException) {
            return appException;
        }
        log.warn("ProviderService call failed ({})", cause.getClass().getSimpleName());
        return new AppException(ErrorCode.PROVIDER_UNAVAILABLE);
    }
}
