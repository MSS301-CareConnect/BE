package mss301.providerservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;

@Slf4j
public class BookingErrorDecoder implements ErrorDecoder {

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

    // Mọi lỗi từ Booking ở đây đều là lỗi tích hợp: không biết caregiver có bận hay không → 503, không đoán "rảnh".
    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            log.error("BookingService rejected service token (HTTP {}) for {}", status, methodKey);
        } else {
            log.warn("BookingService error (HTTP {}) for {}", status, methodKey);
        }
        return new AppException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE);
    }
}
