package mss301.providerservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;


public class MediaClientErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 404) {
            return new AppException(ErrorCode.MEDIA_NOT_FOUND);
        }
        return new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
    }
}
