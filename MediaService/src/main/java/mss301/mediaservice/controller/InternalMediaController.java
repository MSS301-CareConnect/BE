package mss301.mediaservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.mediaservice.dto.response.APIResponse;
import mss301.mediaservice.dto.response.DownloadUrlResponse;
import mss301.mediaservice.dto.response.MediaResponse;
import mss301.mediaservice.enums.MediaPurpose;
import mss301.mediaservice.service.MediaService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/internal/media")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Internal - Media", description = "API nội bộ cho các service CareConnect; yêu cầu X-Internal-Secret.")
@SecurityRequirement(name = "internalSecret")
public class InternalMediaController {

    MediaService mediaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public APIResponse<MediaResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam MediaPurpose purpose,
            @RequestParam(required = false) UUID uploadedBy,
            @RequestParam String sourceService,
            @RequestParam(required = false) String referenceType,
            @RequestParam(required = false) UUID referenceId) {
        return mediaService.upload(file, purpose, uploadedBy, sourceService, referenceType, referenceId);
    }

    @GetMapping("/{mediaFileId}")
    public APIResponse<MediaResponse> get(@PathVariable UUID mediaFileId) {
        return mediaService.get(mediaFileId);
    }

    @GetMapping("/{mediaFileId}/download-url")
    public APIResponse<DownloadUrlResponse> createDownloadUrl(@PathVariable UUID mediaFileId) {
        return mediaService.createDownloadUrl(mediaFileId);
    }

    @DeleteMapping("/{mediaFileId}")
    public APIResponse<Void> delete(@PathVariable UUID mediaFileId) {
        return mediaService.delete(mediaFileId);
    }
}
