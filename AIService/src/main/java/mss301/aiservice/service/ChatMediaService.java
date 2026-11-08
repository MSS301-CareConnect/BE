package mss301.aiservice.service;

import mss301.aiservice.client.dto.ChatMediaUpload;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatMediaService {
    List<ChatMediaUpload> uploadImages(UUID userId, UUID conversationId, List<MultipartFile> images);

    void deleteQuietly(Collection<UUID> mediaFileIds);

    String getDownloadUrl(UUID mediaFileId);
}
