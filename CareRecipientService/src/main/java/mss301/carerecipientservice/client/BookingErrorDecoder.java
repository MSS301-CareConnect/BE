package mss301.carerecipientservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;

@Slf4j
public class BookingErrorDecoder implements ErrorDecoder {

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

    // Đổi mã lỗi HTTP từ BookingService sang ErrorCode của service này.
    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            // Token SERVICE bị từ chối (client chưa được cấp role SERVICE / cấu hình sai) — cần người vận hành xử lý.
            log.error("BookingService rejected service token (HTTP {}) for {}", status, methodKey);
        } else {
            log.warn("BookingService error (HTTP {}) for {}", status, methodKey);
        }
        return new AppException(ErrorCode.BOOKING_UNAVAILABLE);
    }
}
