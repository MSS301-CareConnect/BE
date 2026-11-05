package mss301.carecatalogservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import org.springframework.context.annotation.Bean;

public class MediaClientConfig {

    @Bean
    public RequestInterceptor mediaRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    // 400 = MediaService từ chối file (loại / dung lượng / nội dung không khớp đuôi); 403, 404 = không có ảnh này của
    // Catalog (file của service khác cũng trả 403 — không phân biệt để không lộ file tồn tại).
    @Bean
    public ErrorDecoder mediaErrorDecoder() {
        return (methodKey, response) -> switch (response.status()) {
            case 400, 413, 415 -> new AppException(ErrorCode.CATALOG_IMAGE_INVALID);
            case 403, 404, 409 -> new AppException(ErrorCode.CATALOG_IMAGE_NOT_FOUND);
            default -> new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
        };
    }
}
