package mss301.identityservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.dto.request.RolePermissionsRequest;
import mss301.identityservice.dto.request.RoleRequest;
import mss301.identityservice.dto.request.RoleUpdateRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.RoleResponse;
import mss301.identityservice.security.CurrentUserId;
import mss301.identityservice.service.RoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")
@Tag(name = "Role", description = "ADMIN quản lý role. 4 role nền tảng có quyền cố định; chỉ role bổ sung mới cấu hình được permission.")
public class RoleController {
    RoleService roleService;

    @GetMapping
    public APIResponse<List<RoleResponse>> list(@CurrentUserId UUID actorId) {
        return roleService.list(actorId);
    }

    @PostMapping
    public APIResponse<RoleResponse> create(@CurrentUserId UUID actorId, @Valid @RequestBody RoleRequest request) {
        return roleService.create(actorId, request);
    }

    @PutMapping("/{id}")
    public APIResponse<RoleResponse> update(@CurrentUserId UUID actorId,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody RoleUpdateRequest request) {
        return roleService.update(actorId, id, request);
    }

    @DeleteMapping("/{id}")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return roleService.delete(actorId, id);
    }

    @PutMapping("/{id}/permissions")
    public APIResponse<RoleResponse> updatePermissions(@CurrentUserId UUID actorId,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody RolePermissionsRequest request) {
        return roleService.updatePermissions(actorId, id, request);
    }
}
