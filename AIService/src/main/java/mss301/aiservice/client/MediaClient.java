package mss301.aiservice.client;

import mss301.aiservice.client.dto.DownloadUrlClientResponse;
import mss301.aiservice.client.dto.MediaClientResponse;
import mss301.aiservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@FeignClient(name = "media", url = "${ai.media.url}", configuration = MediaClientConfig.class)
public interface MediaClient {
    @PostMapping(value = "/api/v1/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    RemoteApiResponse<MediaClientResponse> upload(
            @RequestPart("file")
            MultipartFile file,

            @RequestParam("purpose")
            String purpose,

            @RequestParam("uploadedBy")
            UUID uploadedBy,

            @RequestParam("referenceType")
            String referenceType,

            @RequestParam("referenceId")
            UUID referenceId
    );

    @GetMapping("/api/v1/media/{mediaFileId}/download-url")
    RemoteApiResponse<DownloadUrlClientResponse> createDownloadUrl(@PathVariable("mediaFileId") UUID mediaFileId);

    @DeleteMapping("/api/v1/media/{mediaFileId}")
    RemoteApiResponse<Void> delete(@PathVariable("mediaFileId") UUID mediaFileId);
}
