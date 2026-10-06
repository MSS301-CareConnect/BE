package mss301.carecatalogservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.dto.request.CareServiceRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CareServiceResponse;
import mss301.carecatalogservice.service.CareServiceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/care-services")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Care service", description = "Danh mục dịch vụ chuẩn của nền tảng. Chỉ ADMIN được tạo/sửa/xóa.")
public class CareServiceController {
    CareServiceService careServiceService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CareServiceResponse> createCareService(@Valid @RequestBody CareServiceRequest request) {
        return careServiceService.createCareService(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CareServiceResponse> updateCareService(@PathVariable UUID id,
                                                              @Valid @RequestBody CareServiceRequest request) {
        return careServiceService.updateCareService(id, request);
    }

    @GetMapping
    public APIResponse<List<CareServiceResponse>> getAllCareServices() {
        return careServiceService.getAllCareServices();
    }

    @GetMapping("/{id}")
    public APIResponse<CareServiceResponse> getCareServiceById(@PathVariable UUID id) {
        return careServiceService.getCareServiceById(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<Void> deleteCareService(@PathVariable UUID id) {
        return careServiceService.deleteCareService(id);
    }
}
