package mss301.identityservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.dto.request.ChangePasswordRequest;
import mss301.identityservice.dto.request.ProvisionRequest;
import mss301.identityservice.dto.request.UpdateProfileRequest;
import mss301.identityservice.dto.request.UserBatchRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.enums.UserStatus;
import mss301.identityservice.security.CurrentUserId;
import mss301.identityservice.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "User", description = "Quản lý tài khoản cho mọi role (hồ sơ, mật khẩu), thao tác quản trị theo quyền, và tra cứu tài khoản cho service khác.")
public class UserController {
    UserService userService;

    // ===== GET =====

    @GetMapping("/me")
    public APIResponse<UserResponse> me(@CurrentUserId UUID actorId) {
        return userService.me(actorId);
    }

    @GetMapping("/{id}")
    public APIResponse<UserResponse> get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @GetMapping("/by-email")
    public APIResponse<UserResponse> getByEmail(@RequestParam(required = false) String email) {
        return userService.getByEmail(email);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read') and (hasRole('ADMIN') or hasRole('CENTER_MANAGER'))")
    public APIResponse<List<UserResponse>> list(@CurrentUserId UUID actorId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return userService.list(actorId, page, size);
    }

    // ===== POST =====

    @PostMapping("/me/change-password")
    public APIResponse<Void> changePassword(@CurrentUserId UUID actorId,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        return userService.changePassword(actorId, request);
    }

    @PostMapping("/batch")
    public APIResponse<List<UserResponse>> listBatch(@Valid @RequestBody UserBatchRequest request) {
        return userService.listBatch(request);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('CENTER_MANAGER')")
    public APIResponse<UserResponse> create(@CurrentUserId UUID actorId,
                                            @Valid @RequestBody ProvisionRequest request) {
        return userService.provision(actorId, request);
    }

    // ===== PUT =====

    @PutMapping("/me")
    public APIResponse<UserResponse> update(@CurrentUserId UUID actorId,
                                            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.update(actorId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public APIResponse<UserResponse> update(@CurrentUserId UUID actorId,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.update(actorId, id, request);
    }

    @PutMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")
    public APIResponse<UserResponse> assign(@CurrentUserId UUID actorId,
                                            @PathVariable UUID id,
                                            @PathVariable UUID roleId) {
        return userService.assign(actorId, id, roleId);
    }

    // ===== PATCH =====

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:manage')")
    public APIResponse<UserResponse> updateStatus(@CurrentUserId UUID actorId,
                                                  @PathVariable UUID id,
                                                  @RequestParam UserStatus status) {
        return userService.updateStatus(actorId, id, status);
    }

    // ===== DELETE =====

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return userService.delete(actorId, id);
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")
    public APIResponse<UserResponse> revoke(@CurrentUserId UUID actorId,
                                            @PathVariable UUID id,
                                            @PathVariable UUID roleId) {
        return userService.revoke(actorId, id, roleId);
    }
}
