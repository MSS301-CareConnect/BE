package mss301.identityservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.PermissionResponse;
import mss301.identityservice.security.CurrentUserId;
import mss301.identityservice.service.RoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")
@Tag(name = "Permission", description = "Danh mục permission IdentityService hỗ trợ (dạng <resource>:<action>).")
public class PermissionController {
    RoleService roleService;

    @GetMapping
    public APIResponse<List<PermissionResponse>> list(@CurrentUserId UUID actorId) {
        return roleService.listPermissions(actorId);
    }
}
