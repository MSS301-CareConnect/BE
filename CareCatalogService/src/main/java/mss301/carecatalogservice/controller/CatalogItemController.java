package mss301.carecatalogservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.dto.request.CatalogItemRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CatalogItemResponse;
import mss301.carecatalogservice.service.CatalogItemService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/care-services")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Catalog item", description = "Danh mục dịch vụ chuẩn của nền tảng. Chỉ ADMIN được tạo/sửa/xóa.")
public class CatalogItemController {
    CatalogItemService catalogItemService;

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

    @GetMapping
    public APIResponse<List<CatalogItemResponse>> listAll() {
        return catalogItemService.listAll();
    }

    @GetMapping("/{id}")
    public APIResponse<CatalogItemResponse> getById(@PathVariable UUID id) {
        return catalogItemService.getById(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<Void> delete(@PathVariable UUID id) {
        return catalogItemService.delete(id);
    }
}
