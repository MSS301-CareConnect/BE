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
import mss301.identityservice.dto.response.UserStatisticsResponse;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.enums.UserStatus;
import mss301.identityservice.enums.RoleCode;
import mss301.identityservice.dto.request.UserStatusRequest;
import mss301.identityservice.dto.response.AffectedUsersResponse;
import mss301.identityservice.dto.response.PageResponse;
import mss301.identityservice.security.CurrentUserId;
import mss301.identityservice.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "User", description = "Quản lý tài khoản cho mọi role (hồ sơ, mật khẩu), thao tác quản trị theo quyền, và tra cứu tài khoản cho service khác.")
public class UserController {
    UserService userService;

    // API thống kê người dùng trong khoảng ngày.
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<UserStatisticsResponse> statistics(@RequestParam(required = false) LocalDate from,
                                                         @RequestParam(required = false) LocalDate to) {
        return userService.statistics(from, to);
    }

    // API lấy hồ sơ người dùng đang đăng nhập.
    @GetMapping("/me")
    public APIResponse<UserResponse> me(@CurrentUserId UUID actorId) {
        return userService.me(actorId);
    }

    // API xem thông tin một người dùng.
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<UserResponse> get(@PathVariable UUID id) {
        return userService.get(id);
    }

    // API tìm người dùng theo email (cho service khác).
    @GetMapping("/by-email")
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN')")
    public APIResponse<UserResponse> getByEmail(@RequestParam(required = false) String email) {
        return userService.getByEmail(email);
    }

    // API danh sách người dùng có lọc và phân trang.
    @GetMapping
    @PreAuthorize("hasAuthority('user:read') and (hasRole('ADMIN') or hasRole('CENTER_MANAGER'))")
    public APIResponse<PageResponse<UserResponse>> list(@CurrentUserId UUID actorId,
                                                @RequestParam(required = false) RoleCode role,
                                                @RequestParam(required = false) UserStatus status,
                                                @RequestParam(required = false) UUID centerId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "createdAt,desc") String sort,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return userService.list(actorId, role, status, centerId, keyword, page, size, sort);
    }

    // API đổi mật khẩu.
    @PostMapping("/me/change-password")
    public APIResponse<Void> changePassword(@CurrentUserId UUID actorId,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        return userService.changePassword(actorId, request);
    }

    // API lấy thông tin nhiều người dùng một lần (cho service khác).
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<List<UserResponse>> listBatch(@Valid @RequestBody UserBatchRequest request) {
        return userService.listBatch(request);
    }

    // API tạo tài khoản manager / caregiver.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('CENTER_MANAGER')")
    public APIResponse<UserResponse> create(@CurrentUserId UUID actorId,
                                            @Valid @RequestBody ProvisionRequest request) {
        return userService.provision(actorId, request);
    }

    // API cập nhật hồ sơ của chính mình.
    @PutMapping("/me")
    public APIResponse<UserResponse> update(@CurrentUserId UUID actorId,
                                            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.update(actorId, request);
    }

    // API cập nhật hồ sơ của người dùng khác.
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public APIResponse<UserResponse> update(@CurrentUserId UUID actorId,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.update(actorId, id, request);
    }

    // API gán role cho người dùng.
    @PutMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")
    public APIResponse<UserResponse> assign(@CurrentUserId UUID actorId,
                                            @PathVariable UUID id,
                                            @PathVariable UUID roleId) {
        return userService.assign(actorId, id, roleId);
    }

    // API khóa / mở khóa tài khoản qua tham số query.
    @PatchMapping(value = "/{id}/status", params = "status")
    @PreAuthorize("hasAuthority('user:manage')")
    public APIResponse<UserResponse> updateStatus(@CurrentUserId UUID actorId,
                                                  @PathVariable UUID id,
                                                  @RequestParam UserStatus status,
                                                  @RequestParam(required = false) String reason) {
        return userService.updateStatus(actorId, id, status, reason);
    }

    // API khóa / mở khóa tài khoản qua body.
    @PatchMapping(value = "/{id}/status", params = "!status")
    @PreAuthorize("hasAuthority('user:manage')")
    public APIResponse<UserResponse> updateStatus(@CurrentUserId UUID actorId, @PathVariable UUID id,
                                                  @Valid @RequestBody UserStatusRequest request) {
        return userService.updateStatus(actorId, id, request.getStatus(), request.getReason());
    }

    // API khóa / mở mọi tài khoản của một trung tâm (khi trung tâm bị khóa / mở).
    @PatchMapping("/centers/{centerId}/status")
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN')")
    public APIResponse<AffectedUsersResponse> updateCenterStatus(@CurrentUserId UUID actorId,
                                                                 @PathVariable UUID centerId,
                                                                 @Valid @RequestBody UserStatusRequest request) {
        return userService.updateCenterStatus(actorId, centerId, request);
    }

    // API xóa tài khoản.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return userService.delete(actorId, id);
    }

    // API thu hồi role của người dùng.
    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")
    public APIResponse<UserResponse> revoke(@CurrentUserId UUID actorId,
                                            @PathVariable UUID id,
                                            @PathVariable UUID roleId) {
        return userService.revoke(actorId, id, roleId);
    }
}
