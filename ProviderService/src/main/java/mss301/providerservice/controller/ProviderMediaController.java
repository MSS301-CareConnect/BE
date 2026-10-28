package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.ProviderMediaResponse;
import mss301.providerservice.enums.MediaOwnerType;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.ProviderMediaService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/provider-media")
@Tag(name = "Provider Media", description = "File của center, caregiver profile và certificate")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProviderMediaController {

    ProviderMediaService providerMediaService;

    @GetMapping("/{ownerType}/{ownerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER','CUSTOMER')")
    public APIResponse<List<ProviderMediaResponse>> list(@CurrentUserId UUID actorId,
                                                         @PathVariable MediaOwnerType ownerType,
                                                         @PathVariable UUID ownerId) {
        return providerMediaService.list(actorId, ownerType, ownerId);
    }

    @PostMapping(value = "/{ownerType}/{ownerId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<List<ProviderMediaResponse>> upload(@CurrentUserId UUID actorId,
                                                           @PathVariable MediaOwnerType ownerType,
                                                           @PathVariable UUID ownerId,
                                                           @RequestPart("files") List<MultipartFile> files) {
        return providerMediaService.upload(actorId, ownerType, ownerId, files);
    }

    @DeleteMapping("/{ownerType}/{ownerId}/{mediaFileId}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable MediaOwnerType ownerType,
                                    @PathVariable UUID ownerId,
                                    @PathVariable UUID mediaFileId) {
        return providerMediaService.delete(actorId, ownerType, ownerId, mediaFileId);
    }
}
