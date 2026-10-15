package mss301.mediaservice.service;

import mss301.mediaservice.dto.response.APIResponse;
import mss301.mediaservice.dto.response.DownloadUrlResponse;
import mss301.mediaservice.dto.response.MediaResponse;
import mss301.mediaservice.enums.MediaPurpose;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface MediaService {

    APIResponse<MediaResponse> upload(MultipartFile file, MediaPurpose purpose, UUID uploadedBy,
                                      String sourceService, String referenceType, UUID referenceId);

    APIResponse<MediaResponse> get(UUID mediaFileId);
    APIResponse<DownloadUrlResponse> createDownloadUrl(UUID mediaFileId);
    APIResponse<Void> delete(UUID mediaFileId);
}
