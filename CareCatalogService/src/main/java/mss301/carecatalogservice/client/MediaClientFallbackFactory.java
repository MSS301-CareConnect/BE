package mss301.carecatalogservice.client;

import mss301.carecatalogservice.client.dto.DownloadUrlClientResponse;
import mss301.carecatalogservice.client.dto.MediaClientResponse;
import mss301.carecatalogservice.client.dto.RemoteApiResponse;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Component
public class MediaClientFallbackFactory implements FallbackFactory<MediaClient> {
    // Fallback khi gọi MediaService lỗi: ném AppException dịch từ nguyên nhân gốc.
    @Override
    public MediaClient create(Throwable cause) {
        return new MediaClient() {
            @Override
            public RemoteApiResponse<DownloadUrlClientResponse> createDownloadUrl(UUID mediaFileId) {
                throw resolveFailure(cause);
            }

            @Override
            public RemoteApiResponse<MediaClientResponse> upload(MultipartFile file, String purpose, UUID uploadedBy,
                                                                 String referenceType) {
                throw resolveFailure(cause);
            }
        };
    }

    // Lỗi đã được ErrorDecoder đổi thành AppException thì giữ nguyên; còn lại (timeout, mạng, circuit mở) là 503.
    private RuntimeException resolveFailure(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof AppException appException) {
                return appException;
            }
            current = current.getCause();
        }
        return new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
    }
}
