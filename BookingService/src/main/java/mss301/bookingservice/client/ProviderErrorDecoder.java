package mss301.bookingservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;

@Slf4j
public class ProviderErrorDecoder implements ErrorDecoder {

    private static final int NOT_FOUND = 404;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final String CENTER_METHOD = "#getCenter(";

    // Đổi mã lỗi HTTP từ ProviderService sang ErrorCode.
    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == NOT_FOUND) {
            ErrorCode notFound = methodKey.contains(CENTER_METHOD) ? ErrorCode.CENTER_NOT_FOUND : ErrorCode.CAREGIVER_NOT_FOUND;
            return new AppException(notFound);
        }
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            // Token bị từ chối: token SERVICE sai cấu hình / thiếu quyền, hoặc token người dùng không đủ phạm vi.
            log.error("ProviderService rejected credentials (HTTP {}) for {}", status, methodKey);
        } else {
            log.warn("ProviderService error (HTTP {}) for {}", status, methodKey);
        }
        return new AppException(ErrorCode.PROVIDER_UNAVAILABLE);
    }
}
