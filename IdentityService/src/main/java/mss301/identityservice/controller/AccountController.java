package mss301.identityservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.dto.request.*;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.OtpResponse;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.enums.UserStatus;
import mss301.identityservice.security.CurrentUserId;
import mss301.identityservice.service.AccountService;
import mss301.identityservice.service.RegistrationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Account", description = "Quản lý tài khoản cho mọi role (đăng ký, hồ sơ, mật khẩu) và thao tác quản trị theo quyền + phạm vi trung tâm.")
public class AccountController {
    AccountService accountService;
    RegistrationService registrationService;

    // ===== Đăng ký & quên mật khẩu (public) =====

    @PostMapping("/register")
    @SecurityRequirements({})
    public APIResponse<OtpResponse> register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/register/resend-otp")
    @SecurityRequirements({})
    public APIResponse<OtpResponse> resend(@Valid @RequestBody EmailRequest request) {
        return registrationService.resend(request);
    }

    @PostMapping("/register/verify")
    @SecurityRequirements({})
    public APIResponse<UserResponse> verify(@Valid @RequestBody VerifyOtpRequest request) {
        return registrationService.verify(request);
    }

    @PostMapping("/forgot-password")
    @SecurityRequirements({})
    public APIResponse<Void> forgotPassword(@Valid @RequestBody EmailRequest request) {
        return registrationService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    @SecurityRequirements({})
    public APIResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return registrationService.resetPassword(request);
    }

    // ===== Tài khoản của chính mình =====

    @GetMapping("/me")
    public APIResponse<UserResponse> me(@CurrentUserId UUID actorId) {
        return accountService.me(actorId);
    }

    @PutMapping("/me")
    public APIResponse<UserResponse> update(@CurrentUserId UUID actorId,
                                            @Valid @RequestBody UpdateProfileRequest request) {
        return accountService.update(actorId, request);
    }

    @PostMapping("/me/change-password")
    public APIResponse<Void> changePassword(@CurrentUserId UUID actorId,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        return accountService.changePassword(actorId, request);
    }

    // ===== Quản trị tài khoản =====

    @GetMapping
    @PreAuthorize("hasAuthority('user:read') and (hasRole('ADMIN') or hasRole('CENTER_MANAGER'))")
    public APIResponse<List<UserResponse>> list(@CurrentUserId UUID actorId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return accountService.list(actorId, page, size);
    }

    @GetMapping("/{id}")
    public APIResponse<UserResponse> get(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return accountService.get(actorId, id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('CENTER_MANAGER')")
    public APIResponse<UserResponse> create(@CurrentUserId UUID actorId,
                                            @Valid @RequestBody ProvisionRequest request) {
        return accountService.provision(actorId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public APIResponse<UserResponse> update(@CurrentUserId UUID actorId,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody UpdateProfileRequest request) {
        return accountService.update(actorId, id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:manage')")
    public APIResponse<UserResponse> updateStatus(@CurrentUserId UUID actorId,
                                                  @PathVariable UUID id,
                                                  @RequestParam UserStatus status) {
        return accountService.updateStatus(actorId, id, status);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return accountService.delete(actorId, id);
    }

    @PutMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")
    public APIResponse<UserResponse> assign(@CurrentUserId UUID actorId,
                                            @PathVariable UUID id,
                                            @PathVariable UUID roleId) {
        return accountService.assign(actorId, id, roleId);
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")
    public APIResponse<UserResponse> revoke(@CurrentUserId UUID actorId,
                                            @PathVariable UUID id,
                                            @PathVariable UUID roleId) {
        return accountService.revoke(actorId, id, roleId);
    }
}
