package mss301.providerservice.client;

import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AiClientFallbackFactory implements FallbackFactory<AiClient> {

    // Fallback khi gọi AIService lỗi: ném AppException tương ứng.
    @Override
    public AiClient create(Throwable cause) {
        return request -> {
            throw resolveFailure(cause);
        };
    }

    // Lỗi đã dịch (400 → 4703, khác → 4704) giữ nguyên; lỗi mạng / timeout / mạch mở → 503 để scheduler thử lại.
    private AppException resolveFailure(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof AppException appException) {
                return appException;
            }
            current = current.getCause();
        }
        log.warn("AIService unavailable ({})", cause.getClass().getSimpleName());
        return new AppException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }
}
