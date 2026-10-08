package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterStatusUpdateRequest;
import mss301.providerservice.dto.request.CenterUpdateRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CenterResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CareCenterService;
import mss301.providerservice.service.IdentityAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/centers")
@Tag(name = "Admin Care Centers", description = "Quản trị trung tâm chăm sóc")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminCareCenterController {

    CareCenterService careCenterService;
    IdentityAccountService identityAccountService;

    @PostMapping
    public APIResponse<CenterResponse> create(@CurrentUserId UUID actorId,
                                              @RequestBody @Valid CenterCreateRequest request) {
        if (request.getManagerUserId() != null) {
            identityAccountService.requireActiveRole(request.getManagerUserId(), "CENTER_MANAGER");
        }
        return careCenterService.create(actorId, request);
    }

    @GetMapping
    public APIResponse<PageResponse<CenterResponse>> list(
            @RequestParam(required = false) CenterStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return careCenterService.list(status, keyword, includeDeleted, page, size);
    }

    @GetMapping("/{id}")
    public APIResponse<CenterResponse> get(@PathVariable UUID id) {
        return careCenterService.get(id);
    }

    @PutMapping("/{id}")
    public APIResponse<CenterResponse> update(@CurrentUserId UUID actorId,
                                              @PathVariable UUID id,
                                              @RequestBody @Valid CenterUpdateRequest request) {
        if (request.getManagerUserId() != null) {
            identityAccountService.requireActiveRole(request.getManagerUserId(), "CENTER_MANAGER");
        }
        return careCenterService.update(actorId, id, request);
    }

    @PatchMapping("/{id}/status")
    public APIResponse<CenterResponse> updateStatus(@CurrentUserId UUID actorId,
                                                    @PathVariable UUID id,
                                                    @RequestBody @Valid CenterStatusUpdateRequest request) {
        return careCenterService.updateStatus(actorId, id, request);
    }

    @DeleteMapping("/{id}")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID id,
                                    @RequestParam Long version) {
        return careCenterService.delete(actorId, id, version);
    }
}
