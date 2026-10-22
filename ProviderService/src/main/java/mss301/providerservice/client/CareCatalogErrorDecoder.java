package mss301.providerservice.client;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;

@Slf4j
public class CareCatalogErrorDecoder implements ErrorDecoder {

    private static final int NOT_FOUND = 404;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final String GET_CENTER_SERVICE_METHOD = "#getCenterService(";

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        // Với token SERVICE, 404 của GET /center-services/{id} = offering không tồn tại hoặc không còn bán được → không
        // gán làm kỹ năng. 404 của endpoint khác (vd price-summary chưa triển khai) là lỗi tích hợp → 503.
        if (status == NOT_FOUND && methodKey.contains(GET_CENTER_SERVICE_METHOD)) {
            return new AppException(ErrorCode.CAREGIVER_SERVICE_NOT_ACTIVE);
        }
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            log.error("CareCatalogService rejected service token (HTTP {}) for {}", status, methodKey);
        } else {
            log.warn("CareCatalogService error (HTTP {}) for {}", status, methodKey);
        }
        return new AppException(ErrorCode.CATALOG_UNAVAILABLE);
    }
}
