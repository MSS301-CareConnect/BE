package mss301.providerservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;

@Slf4j
public class AiErrorDecoder implements ErrorDecoder {

    private static final int BAD_REQUEST = 400;

    // 400 = AIService từ chối dữ liệu (lỗi vĩnh viễn, không thử lại); còn lại (token, Groq quá tải, 5xx) → thử lại sau.
    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == BAD_REQUEST) {
            log.error("AIService rejected certificate check request (HTTP 400) for {}", methodKey);
            return new AppException(ErrorCode.REMOTE_COMMAND_REJECTED);
        }
        log.warn("AIService error (HTTP {}) for {}", status, methodKey);
        return new AppException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }
}
