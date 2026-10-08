package mss301.providerservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.exception.*;

@Slf4j
public class IdentityErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 404) return new AppException(ErrorCode.IDENTITY_USER_NOT_FOUND);
        log.warn("IdentityService rejected/failed request {} with HTTP {}", methodKey, response.status());
        return new AppException(ErrorCode.IDENTITY_UNAVAILABLE);
    }
}
