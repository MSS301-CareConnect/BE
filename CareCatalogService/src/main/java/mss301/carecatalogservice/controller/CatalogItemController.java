package mss301.carecatalogservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.dto.request.*;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CatalogImageResponse;
import mss301.carecatalogservice.dto.response.CatalogItemResponse;
import mss301.carecatalogservice.dto.response.PageResponse;
import mss301.carecatalogservice.enums.RoleCode;
import mss301.carecatalogservice.security.CurrentRoles;
import mss301.carecatalogservice.security.CurrentUserId;
import mss301.carecatalogservice.service.CatalogItemService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/catalog-items", "/api/v1/care-services"})
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Catalog item", description = "Danh mục dịch vụ chuẩn của nền tảng. Chỉ ADMIN được tạo/sửa/xóa.")
public class CatalogItemController {
    CatalogItemService catalogItemService;

    @GetMapping
    @PreAuthorize("permitAll()")
    public APIResponse<PageResponse<CatalogItemResponse>> list(@CurrentRoles Set<RoleCode> roles,
                                                               @ParameterObject CatalogItemSearchRequest request) {
        return catalogItemService.list(roles, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("permitAll()")
    public APIResponse<CatalogItemResponse> get(@CurrentRoles Set<RoleCode> roles, @PathVariable UUID id) {
        return catalogItemService.get(roles, id);
    }

    // Public, không đọc token: ảnh dịch vụ hiển thị cho cả khách chưa đăng nhập. 302 tới URL tải có hạn của R2.
    @GetMapping("/images/{mediaFileId}")
    @SecurityRequirements({})
    public ResponseEntity<Void> redirectToImage(@PathVariable UUID mediaFileId) {
        return catalogItemService.redirectToImage(mediaFileId);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CatalogItemResponse> create(@Valid @RequestBody CatalogItemRequest request) {
        return catalogItemService.create(request);
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CatalogImageResponse> uploadImage(@CurrentUserId UUID actorId,
                                                         @RequestPart("file") MultipartFile file) {
        return catalogItemService.uploadImage(actorId, file);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CatalogItemResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody CatalogItemRequest request) {
        return catalogItemService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CatalogItemResponse> updateStatus(@PathVariable UUID id,
                                                         @Valid @RequestBody ServiceStatusRequest request) {
        return catalogItemService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<Void> delete(@PathVariable UUID id) {
        return catalogItemService.delete(id);
    }
}
