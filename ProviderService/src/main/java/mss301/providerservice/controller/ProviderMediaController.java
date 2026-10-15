package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/provider-media")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
@Tag(name = "Provider Media", description = "File của center, caregiver profile và certificate")
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class ProviderMediaController {
    ProviderMediaService providerMediaService;

    @PostMapping(value = "/{ownerType}/{ownerId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload nhiều file cho một owner")
    public APIResponse<List<ProviderMediaResponse>> upload(@CurrentUserId UUID actorId, @PathVariable MediaOwnerType ownerType, @PathVariable UUID ownerId,
                                                           @RequestPart("files") List<MultipartFile> files) {
        return providerMediaService.upload(actorId, ownerType, ownerId, files);
    }

    @GetMapping("/{ownerType}/{ownerId}")
    @Operation(summary = "Xem các file và URL tải tạm thời")
    public APIResponse<List<ProviderMediaResponse>> list(@CurrentUserId UUID actorId, @PathVariable MediaOwnerType ownerType,
                                                         @PathVariable UUID ownerId) {
        return providerMediaService.list(actorId, ownerType, ownerId);
    }

    @DeleteMapping("/{ownerType}/{ownerId}/{mediaFileId}")
    @Operation(summary = "Xóa một file khỏi owner và khỏi R2")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId, @PathVariable MediaOwnerType ownerType,
                                    @PathVariable UUID ownerId, @PathVariable UUID mediaFileId) {
        return providerMediaService.delete(actorId, ownerType, ownerId, mediaFileId);
    }
}
