package mss301.providerservice.client;

import mss301.providerservice.client.dto.DownloadUrlClientResponse;
import mss301.providerservice.client.dto.MediaClientResponse;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Component
public class MediaClientFallbackFactory implements FallbackFactory<MediaClient> {
    @Override
    public MediaClient create(Throwable cause) {
        return new MediaClient() {
            @Override
            public APIResponse<MediaClientResponse> upload(MultipartFile file, String purpose, UUID uploadedBy,
                                                           String referenceType,
                                                           UUID referenceId) {
                throw resolveFailure(cause);
            }

            @Override
            public APIResponse<MediaClientResponse> get(UUID mediaFileId) {
                throw resolveFailure(cause);
            }

            @Override
            public APIResponse<DownloadUrlClientResponse> createDownloadUrl(UUID mediaFileId) {
                throw resolveFailure(cause);
            }

            @Override
            public APIResponse<Void> delete(UUID mediaFileId) {
                throw resolveFailure(cause);
            }
        };
    }

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
