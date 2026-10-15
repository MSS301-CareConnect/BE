package mss301.providerservice.client;

import mss301.providerservice.client.dto.DownloadUrlClientResponse;
import mss301.providerservice.client.dto.MediaClientResponse;
import mss301.providerservice.dto.response.APIResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@FeignClient(name = "media", url = "${provider.media.url}",
        configuration = MediaClientConfig.class,
        fallbackFactory = MediaClientFallbackFactory.class)
public interface MediaClient {
    @PostMapping(value = "/internal/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    APIResponse<MediaClientResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("purpose") String purpose,
            @RequestParam("uploadedBy") UUID uploadedBy,
            @RequestParam("sourceService") String sourceService,
            @RequestParam("referenceType") String referenceType,
            @RequestParam("referenceId") UUID referenceId);

    @GetMapping("/internal/media/{mediaFileId}")
    APIResponse<MediaClientResponse> get(@PathVariable("mediaFileId") UUID mediaFileId);

    @GetMapping("/internal/media/{mediaFileId}/download-url")
    APIResponse<DownloadUrlClientResponse> createDownloadUrl(@PathVariable("mediaFileId") UUID mediaFileId);

    @DeleteMapping("/internal/media/{mediaFileId}")
    APIResponse<Void> delete(@PathVariable("mediaFileId") UUID mediaFileId);
}
