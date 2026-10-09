package mss301.bookingservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;

/**
 * HTTP lỗi từ IdentityService → IDENTITY_UNAVAILABLE (503). Booking chỉ gọi batch (id không có bị bỏ qua, không 404)
 * nên mọi lỗi đều là lỗi tích hợp/hạ tầng — không lộ chi tiết cho client.
 */
@Slf4j
public class IdentityErrorDecoder implements ErrorDecoder {

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            // Sai/thiếu BOOKING_IDENTITY_INTERNAL_SECRET — lỗi cấu hình, cần người vận hành xử lý.
            log.error("IdentityService rejected internal credentials (HTTP {}) for {}", status, methodKey);
        } else {
            log.warn("IdentityService error (HTTP {}) for {}", status, methodKey);
        }
        return new AppException(ErrorCode.IDENTITY_UNAVAILABLE);
    }
}
