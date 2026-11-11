package mss301.carerecipientservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;

@Slf4j
public class IdentityErrorDecoder implements ErrorDecoder {

    private static final int NOT_FOUND = 404;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int SERVER_ERROR = 500;

    // Đổi mã lỗi HTTP từ IdentityService (404, 401/403…) sang ErrorCode.
    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == NOT_FOUND) {
            return new AppException(ErrorCode.IDENTITY_USER_NOT_FOUND);
        }
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            // Token người dùng được chuyển tiếp bị Identity từ chối (hết hạn / thiếu quyền) — không đoán, coi như Identity lỗi.
            log.error("IdentityService rejected forwarded credentials (HTTP {}) for {}", status, methodKey);
        } else if (status >= SERVER_ERROR) {
            log.warn("IdentityService error (HTTP {}) for {}", status, methodKey);
        } else {
            log.error("Unexpected IdentityService response (HTTP {}) for {}", status, methodKey);
        }
        return new AppException(ErrorCode.IDENTITY_UNAVAILABLE);
    }
}
