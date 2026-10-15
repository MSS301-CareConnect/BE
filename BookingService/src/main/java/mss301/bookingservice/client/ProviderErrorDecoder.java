package mss301.bookingservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;

/**
 * HTTP lỗi từ ProviderService → AppException của service này. 404 là lỗi nghiệp vụ (trung tâm / caregiver không tồn
 * tại — phân biệt theo method gọi); còn lại → PROVIDER_UNAVAILABLE (503).
 */
@Slf4j
public class ProviderErrorDecoder implements ErrorDecoder {

    private static final int NOT_FOUND = 404;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final String CENTER_METHOD = "#getCenter(";

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == NOT_FOUND) {
            ErrorCode notFound = methodKey.contains(CENTER_METHOD) ? ErrorCode.CENTER_NOT_FOUND : ErrorCode.CAREGIVER_NOT_FOUND;
            return new AppException(notFound);
        }
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            // Sai/thiếu BOOKING_PROVIDER_INTERNAL_SECRET — lỗi cấu hình.
            log.error("ProviderService rejected internal credentials (HTTP {}) for {}", status, methodKey);
        } else {
            log.warn("ProviderService error (HTTP {}) for {}", status, methodKey);
        }
        return new AppException(ErrorCode.PROVIDER_UNAVAILABLE);
    }
}
