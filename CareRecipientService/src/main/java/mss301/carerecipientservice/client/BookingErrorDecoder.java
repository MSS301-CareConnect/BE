package mss301.carerecipientservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;

/**
 * HTTP lỗi từ BookingService → BOOKING_UNAVAILABLE (503). caregiver-access luôn trả 200 khi hợp lệ nên mọi lỗi đều là
 * lỗi tích hợp / hạ tầng — không đoán là "được phép".
 */
@Slf4j
public class BookingErrorDecoder implements ErrorDecoder {

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

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
