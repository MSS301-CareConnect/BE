package mss301.aiservice.service.impl;

import feign.FeignException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.client.MediaClient;
import mss301.aiservice.client.dto.ChatMediaUpload;
import mss301.aiservice.client.dto.DownloadUrlClientResponse;
import mss301.aiservice.client.dto.MediaClientResponse;
import mss301.aiservice.client.dto.RemoteApiResponse;
import mss301.aiservice.exception.AppException;
import mss301.aiservice.exception.ErrorCode;
import mss301.aiservice.mapper.MediaClientMapper;
import mss301.aiservice.service.ChatMediaService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMediaServiceImpl implements ChatMediaService {
    static String CHAT_ATTACHMENT_PURPOSE = "CHAT_ATTACHMENT";

    static String CHAT_REFERENCE_TYPE = "CHAT_CONVERSATION";

    MediaClient mediaClient;
    MediaClientMapper mediaClientMapper;

    @Override
    public List<ChatMediaUpload> uploadImages(UUID userId, UUID conversationId, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }

        if (userId == null || conversationId == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        List<ChatMediaUpload> uploadedImages = new ArrayList<>();
        List<UUID> uploadedMediaFileIds = new ArrayList<>();

        try {
            for (MultipartFile image : images) {
                MediaClientResponse media = uploadOne(userId, conversationId, image);
                uploadedMediaFileIds.add(media.getMediaFileId());

                DownloadUrlClientResponse download = requireDownloadUrl(media.getMediaFileId());
                uploadedImages.add(mediaClientMapper.toChatMediaUpload(media, download));
            }

            return uploadedImages;
        } catch (RuntimeException exception) {
            deleteQuietly(uploadedMediaFileIds);

            if (exception instanceof AppException) {
                throw exception;
            }

            throw new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public String getDownloadUrl(UUID mediaFileId) {
        return requireDownloadUrl(mediaFileId).getDownloadUrl();
    }

    @Override
    public void deleteQuietly(Collection<UUID> mediaFileIds) {
        if (mediaFileIds == null || mediaFileIds.isEmpty()) {
            return;
        }

        for (UUID mediaFileId : mediaFileIds) {
            if (mediaFileId == null) {
                continue;
            }

            try {
                mediaClient.delete(mediaFileId);
            } catch (RuntimeException exception) {
                log.warn("Cannot clean chat media file {} ({})", mediaFileId, exception.getClass().getSimpleName());
            }
        }

    }

    private MediaClientResponse uploadOne(UUID userId, UUID conversationId, MultipartFile image) {
        try {
            RemoteApiResponse<MediaClientResponse> response = mediaClient.upload(image, CHAT_ATTACHMENT_PURPOSE, userId,
                            CHAT_REFERENCE_TYPE,
                            conversationId
                    );

            if (response == null || response.getResult() == null || response
                            .getResult()
                            .getMediaFileId() == null
            ) {
                throw new AppException(ErrorCode.MEDIA_UPLOAD_FAILED);
            }

            return response.getResult();
        } catch (FeignException exception) {
            log.warn("Media upload rejected with HTTP {}", exception.status());
            throw new AppException(ErrorCode.MEDIA_UPLOAD_FAILED);
        }
    }

    private DownloadUrlClientResponse requireDownloadUrl(UUID mediaFileId) {
        try {
            RemoteApiResponse<DownloadUrlClientResponse> response = mediaClient.createDownloadUrl(mediaFileId);

            if (response == null
                    || response.getResult() == null
                    || response
                    .getResult()
                    .getDownloadUrl() == null
                    || response
                    .getResult()
                    .getDownloadUrl()
                    .isBlank()
            ) {
                throw new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
            }

            return response.getResult();
        } catch (FeignException exception) {
            throw new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
        }
    }
}
