package mss301.mediaservice.service;

import mss301.mediaservice.dto.response.APIResponse;
import mss301.mediaservice.dto.response.DownloadUrlResponse;
import mss301.mediaservice.dto.response.MediaResponse;
import mss301.mediaservice.enums.MediaPurpose;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface MediaService {

    // Upload file và lưu metadata.
    APIResponse<MediaResponse> upload(MultipartFile file, MediaPurpose purpose, UUID uploadedBy,
                                      String referenceType, UUID referenceId);

    // Lấy metadata của file.
    APIResponse<MediaResponse> get(UUID mediaFileId);
    // Tạo URL tải file có thời hạn.
    APIResponse<DownloadUrlResponse> createDownloadUrl(UUID mediaFileId);
    // Xóa file.
    APIResponse<Void> delete(UUID mediaFileId);
}
