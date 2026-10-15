package mss301.mediaservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.mediaservice.config.MediaProperties;
import mss301.mediaservice.dto.response.APIResponse;
import mss301.mediaservice.dto.response.DownloadUrlResponse;
import mss301.mediaservice.dto.response.MediaResponse;
import mss301.mediaservice.entity.MediaFile;
import mss301.mediaservice.enums.FileType;
import mss301.mediaservice.enums.MediaPurpose;
import mss301.mediaservice.enums.MediaStatus;
import mss301.mediaservice.exception.AppException;
import mss301.mediaservice.exception.ErrorCode;
import mss301.mediaservice.mapper.MediaMapper;
import mss301.mediaservice.repository.MediaFileRepository;
import mss301.mediaservice.service.MediaService;
import mss301.mediaservice.service.R2StorageService;
import mss301.mediaservice.util.FileContentInspector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class MediaServiceImpl implements MediaService {

    static Pattern SOURCE_SERVICE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,49}");

    MediaFileRepository mediaFileRepository;
    MediaMapper mediaMapper;
    R2StorageService r2StorageService;
    MediaProperties mediaProperties;
    TransactionTemplate transactionTemplate;

    @Override
    public APIResponse<MediaResponse> upload(MultipartFile file, MediaPurpose purpose, UUID uploadedBy,
                                             String sourceService, String referenceType, UUID referenceId) {
        String contentType = validateUpload(file, purpose, sourceService);
        String objectKey = buildObjectKey(purpose, FileContentInspector.extension(contentType));
        String etag;
        try (InputStream inputStream = file.getInputStream()) {
            etag = r2StorageService.upload(objectKey, contentType, file.getSize(), inputStream);
        } catch (IOException exception) {
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }

        try {
            MediaFile savedMedia = transactionTemplate.execute(status -> {
                MediaFile mediaFile = MediaFile.builder()
                        .bucketName(mediaProperties.getR2().getBucket())
                        .objectKey(objectKey)
                        .originalFileName(sanitizeOriginalFileName(file.getOriginalFilename()))
                        .contentType(contentType)
                        .fileType(resolveFileType(contentType))
                        .fileSize(file.getSize())
                        .mediaPurpose(purpose)
                        .mediaStatus(MediaStatus.ACTIVE)
                        .uploadedBy(uploadedBy)
                        .sourceService(sourceService)
                        .referenceType(normalizeOptional(referenceType))
                        .referenceId(referenceId)
                        .etag(etag)
                        .build();
                return mediaFileRepository.save(mediaFile);
            });
            MediaResponse response = mediaMapper.toResponse(savedMedia);
            log.info("Media uploaded: mediaFileId={}, purpose={}, sourceService={}",
                    response.getMediaFileId(), purpose, sourceService);
            return APIResponse.success("Tải file lên thành công", response);
        } catch (RuntimeException exception) {
            cleanupObject(objectKey);
            throw exception;
        }
    }

    @Override
    public APIResponse<MediaResponse> get(UUID mediaFileId) {
        MediaFile mediaFile = findActiveMedia(mediaFileId);
        MediaResponse response = mediaMapper.toResponse(mediaFile);
        return APIResponse.success("Lấy metadata file thành công", response);
    }

    @Override
    public APIResponse<DownloadUrlResponse> createDownloadUrl(UUID mediaFileId) {
        MediaFile mediaFile = findActiveMedia(mediaFileId);
        String downloadUrl = r2StorageService.createDownloadUrl(mediaFile.getObjectKey());
        long ttl = mediaProperties.getR2().getDownloadUrlTtlSeconds();
        DownloadUrlResponse response = DownloadUrlResponse.builder()
                .mediaFileId(mediaFileId)
                .downloadUrl(downloadUrl)
                .expiresAt(Instant.now().plusSeconds(ttl))
                .build();
        return APIResponse.success("Tạo đường dẫn tải file thành công", response);
    }

    @Override
    public APIResponse<Void> delete(UUID mediaFileId) {
        MediaFile pendingMedia = transactionTemplate.execute(status -> {
            MediaFile mediaFile = findExistingMedia(mediaFileId);
            if (mediaFile.getMediaStatus() == MediaStatus.DELETED) {
                return mediaFile;
            }
            mediaFile.setMediaStatus(MediaStatus.DELETE_PENDING);
            return mediaFileRepository.save(mediaFile);
        });

        if (pendingMedia.getMediaStatus() == MediaStatus.DELETED) {
            return APIResponse.success("File đã được xóa trước đó");
        }

        r2StorageService.delete(pendingMedia.getObjectKey());
        transactionTemplate.executeWithoutResult(status -> {
            MediaFile mediaFile = findExistingMedia(mediaFileId);
            mediaFile.setMediaStatus(MediaStatus.DELETED);
            mediaFile.setDeletedAt(Instant.now());
            mediaFileRepository.save(mediaFile);
        });
        log.info("Media deleted: mediaFileId={}", mediaFileId);
        return APIResponse.success("Xóa file thành công");
    }

    private String validateUpload(MultipartFile file, MediaPurpose purpose, String sourceService) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_REQUIRED);
        }
        if (purpose == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (file.getSize() > purpose.getMaximumBytes()) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }
        contentType = contentType.toLowerCase(Locale.ROOT);
        if (!purpose.getAllowedContentTypes().contains(contentType)) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }
        if (sourceService == null || !SOURCE_SERVICE_PATTERN.matcher(sourceService).matches()) {
            throw new AppException(ErrorCode.INVALID_SOURCE_SERVICE);
        }
        FileContentInspector.check(file, contentType);
        return contentType;
    }

    private MediaFile findExistingMedia(UUID mediaFileId) {
        return mediaFileRepository.findById(mediaFileId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));
    }

    private MediaFile findActiveMedia(UUID mediaFileId) {
        MediaFile mediaFile = mediaFileRepository.findByIdAndDeletedAtIsNull(mediaFileId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));
        if (mediaFile.getMediaStatus() != MediaStatus.ACTIVE) {
            throw new AppException(ErrorCode.MEDIA_NOT_ACTIVE);
        }
        return mediaFile;
    }

    private String buildObjectKey(MediaPurpose purpose, String extension) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String folder = purpose.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return "%s/%d/%02d/%s.%s".formatted(
                folder,
                now.getYear(),
                now.getMonthValue(),
                UUID.randomUUID(),
                extension);
    }

    private FileType resolveFileType(String contentType) {
        if (contentType.startsWith("image/")) {
            return FileType.IMAGE;
        }
        if (contentType.startsWith("video/")) {
            return FileType.VIDEO;
        }
        return FileType.DOCUMENT;
    }

    private String sanitizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "file";
        }
        String sanitized = originalFileName.replace('\\', '/');
        sanitized = sanitized.substring(sanitized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "_")
                .trim();
        if (sanitized.isBlank()) {
            return "file";
        }
        return sanitized.length() > 255 ? sanitized.substring(sanitized.length() - 255) : sanitized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void cleanupObject(String objectKey) {
        try {
            r2StorageService.delete(objectKey);
        } catch (RuntimeException cleanupException) {
            log.error("Cannot clean orphan R2 object after database failure ({})",
                    cleanupException.getClass().getSimpleName());
        }
    }
}
