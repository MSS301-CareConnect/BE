package mss301.bookingservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;

@Slf4j
public class CareCatalogErrorDecoder implements ErrorDecoder {

    private static final int NOT_FOUND = 404;
    private static final int BAD_REQUEST = 400;
    private static final int CONFLICT = 409;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == NOT_FOUND) {
            return new AppException(ErrorCode.PRICE_PLAN_NOT_FOUND);
        }
        if (status == BAD_REQUEST || status == CONFLICT) {
            return new AppException(ErrorCode.CENTER_SERVICE_NOT_SUPPORTED);
        }
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            log.error("CareCatalogService rejected credentials (HTTP {}) for {}", status, methodKey);
        } else {
            log.warn("CareCatalogService error (HTTP {}) for {}", status, methodKey);
        }
        return new AppException(ErrorCode.CARE_CATALOG_UNAVAILABLE);
    }
}
