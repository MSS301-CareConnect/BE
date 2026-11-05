package mss301.carecatalogservice.client;

import mss301.carecatalogservice.client.dto.DownloadUrlClientResponse;
import mss301.carecatalogservice.client.dto.MediaClientResponse;
import mss301.carecatalogservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * MediaService qua gateway bằng token SERVICE của Catalog. MediaService chỉ trả file do chính client {@code catalog}
 * tải lên, nên Catalog không thể dùng client này để đọc file của service khác.
 */
@FeignClient(name = "media", url = "${care-catalog.media.url}",
        configuration = MediaClientConfig.class,
        fallbackFactory = MediaClientFallbackFactory.class)
public interface MediaClient {

    @GetMapping("/api/v1/media/{mediaFileId}/download-url")
    RemoteApiResponse<DownloadUrlClientResponse> createDownloadUrl(@PathVariable("mediaFileId") UUID mediaFileId);

    @PostMapping(value = "/api/v1/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    RemoteApiResponse<MediaClientResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("purpose") String purpose,
            @RequestParam("uploadedBy") UUID uploadedBy,
            @RequestParam("referenceType") String referenceType);
}
