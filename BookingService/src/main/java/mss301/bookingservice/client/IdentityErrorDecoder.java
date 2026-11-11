package mss301.bookingservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;

@Slf4j
public class IdentityErrorDecoder implements ErrorDecoder {

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

    // Đổi mã lỗi HTTP từ IdentityService sang ErrorCode.
    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            // Token bị từ chối (forward token người dùng hết hạn / thiếu quyền) — cần người vận hành kiểm tra.
            log.error("IdentityService rejected credentials (HTTP {}) for {}", status, methodKey);
        } else {
            log.warn("IdentityService error (HTTP {}) for {}", status, methodKey);
        }
        return new AppException(ErrorCode.IDENTITY_UNAVAILABLE);
    }
}
