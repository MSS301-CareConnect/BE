package mss301.mediaservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.mediaservice.config.MediaProperties;
import mss301.mediaservice.exception.AppException;
import mss301.mediaservice.exception.ErrorCode;
import mss301.mediaservice.service.R2StorageService;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class R2StorageServiceImpl implements R2StorageService {

    S3Client s3Client;
    S3Presigner s3Presigner;
    MediaProperties mediaProperties;

    @Override
    public String upload(String objectKey, String contentType, long contentLength, InputStream inputStream) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(mediaProperties.getR2().getBucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        Path uploadFile = null;
        try {
            // SDK cần đọc lại khi ký/retry; dùng file tạm để không giữ cả video lớn trong RAM.
            uploadFile = Files.createTempFile("careconnect-media-", ".upload");
            Files.copy(inputStream, uploadFile, StandardCopyOption.REPLACE_EXISTING);
            PutObjectResponse response = s3Client.putObject(
                    request,
                    RequestBody.fromFile(uploadFile));
            return response.eTag();
        } catch (IOException exception) {
            log.error("Không chuẩn bị được file upload ({})", exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        } catch (S3Exception exception) {
            log.error("R2 upload failed with status {}", exception.statusCode());
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        } catch (RuntimeException exception) {
            log.error("R2 upload failed ({})", exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        } finally {
            if (uploadFile != null) {
                deleteTemporaryUpload(uploadFile);
            }
        }
    }

    @Override
    public String createDownloadUrl(String objectKey) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(
                        mediaProperties.getR2().getDownloadUrlTtlSeconds()))
                .getObjectRequest(builder -> builder
                        .bucket(mediaProperties.getR2().getBucket())
                        .key(objectKey))
                .build();
        try {
            return s3Presigner.presignGetObject(request).url().toString();
        } catch (RuntimeException exception) {
            log.error("R2 download URL signing failed ({})", exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.DOWNLOAD_URL_FAILED);
        }
    }

    @Override
    public void delete(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(mediaProperties.getR2().getBucket())
                .key(objectKey)
                .build();
        try {
            s3Client.deleteObject(request);
        } catch (S3Exception exception) {
            log.error("R2 delete failed with status {}", exception.statusCode());
            throw new AppException(ErrorCode.DELETE_FILE_FAILED);
        } catch (RuntimeException exception) {
            log.error("R2 delete failed ({})", exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.DELETE_FILE_FAILED);
        }
    }

    private void deleteTemporaryUpload(Path uploadFile) {
        try {
            Files.deleteIfExists(uploadFile);
        } catch (IOException exception) {
            log.warn("Không dọn được file upload tạm ({})", exception.getClass().getSimpleName());
        }
    }
}
