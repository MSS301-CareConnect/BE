package mss301.aiservice.service;

import mss301.aiservice.client.dto.ChatMediaUpload;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatMediaService {
    // Upload ảnh đính kèm của tin nhắn lên MediaService.
    List<ChatMediaUpload> uploadImages(UUID userId, UUID conversationId, List<MultipartFile> images);

    // Xóa các ảnh trên MediaService, lỗi chỉ ghi log.
    void deleteQuietly(Collection<UUID> mediaFileIds);

    // Lấy URL tải ảnh có thời hạn.
    String getDownloadUrl(UUID mediaFileId);
}
