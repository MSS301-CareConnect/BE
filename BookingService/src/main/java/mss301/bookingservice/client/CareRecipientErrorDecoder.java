package mss301.bookingservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;

/**
 * HTTP lỗi từ CareRecipientService → AppException của service này. 404 (hồ sơ không tồn tại / đã lưu trữ) là lỗi
 * nghiệp vụ; mọi trường hợp khác → CARE_RECIPIENT_UNAVAILABLE (503), không đoán.
 */
@Slf4j
public class CareRecipientErrorDecoder implements ErrorDecoder {

    private static final int NOT_FOUND = 404;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == NOT_FOUND) {
            return new AppException(ErrorCode.RECIPIENT_NOT_FOUND);
        }
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            // Token bị từ chối: token SERVICE sai cấu hình / thiếu quyền, hoặc token người dùng không đủ phạm vi.
            log.error("CareRecipientService rejected credentials (HTTP {}) for {}", status, methodKey);
        } else {
            log.warn("CareRecipientService error (HTTP {}) for {}", status, methodKey);
        }
        return new AppException(ErrorCode.CARE_RECIPIENT_UNAVAILABLE);
    }
}
