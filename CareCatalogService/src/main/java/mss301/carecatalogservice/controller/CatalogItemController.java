package mss301.carecatalogservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.dto.request.*;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CatalogItemResponse;
import mss301.carecatalogservice.dto.response.PageResponse;
import mss301.carecatalogservice.enums.RoleCode;
import mss301.carecatalogservice.security.CurrentRoles;
import mss301.carecatalogservice.service.CatalogItemService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @PreAuthorize("isAuthenticated()")
    public APIResponse<PageResponse<CatalogItemResponse>> list(@CurrentRoles Set<RoleCode> roles,
                                                               @ParameterObject CatalogItemSearchRequest request) {
        return catalogItemService.list(roles, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CatalogItemResponse> get(@CurrentRoles Set<RoleCode> roles, @PathVariable UUID id) {
        return catalogItemService.get(roles, id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CatalogItemResponse> create(@Valid @RequestBody CatalogItemRequest request) {
        return catalogItemService.create(request);
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
