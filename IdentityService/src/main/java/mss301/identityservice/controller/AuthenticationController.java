package mss301.identityservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.dto.request.EmailRequest;
import mss301.identityservice.dto.request.LoginRequest;
import mss301.identityservice.dto.request.RefreshRequest;
import mss301.identityservice.dto.request.RegisterRequest;
import mss301.identityservice.dto.request.ResetPasswordRequest;
import mss301.identityservice.dto.request.VerifyOtpRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.AuthenticationResponse;
import mss301.identityservice.dto.response.CsrfResponse;
import mss301.identityservice.dto.response.OtpResponse;
import mss301.identityservice.dto.response.SessionResponse;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.security.CurrentUserId;
import mss301.identityservice.security.TokenClaims;
import mss301.identityservice.service.AuthenticationService;
import mss301.identityservice.service.RegistrationService;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Authentication", description = "Xác thực, đăng ký, OTP, phiên đăng nhập hợp nhất cho Web và Mobile.")
public class AuthenticationController {

    AuthenticationService authenticationService;
    RegistrationService registrationService;

    // =========================================================================
    // GET Endpoints
    // =========================================================================

    @GetMapping("/csrf")
    @SecurityRequirements({})
    @Operation(summary = "Lấy CSRF token cho client Web")
    public APIResponse<CsrfResponse> csrf(CsrfToken csrfToken) {
        return authenticationService.getCsrf(csrfToken);
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin tài khoản người dùng hiện tại")
    public APIResponse<UserResponse> me(@CurrentUserId UUID actorId) {
        return authenticationService.me(actorId);
    }

    @GetMapping("/sessions")
    @Operation(summary = "Danh sách phiên đăng nhập của người dùng")
    public APIResponse<List<SessionResponse>> sessions(@CurrentUserId UUID actorId,
                                                       HttpServletRequest httpRequest) {
        return authenticationService.listSessions(actorId, httpRequest);
    }

    // =========================================================================
    // POST Endpoints
    // =========================================================================

    @PostMapping("/register")
    @SecurityRequirements({})
    @Operation(summary = "Đăng ký tài khoản (bước 1: gửi OTP)")
    public APIResponse<OtpResponse> register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/register/resend-otp")
    @SecurityRequirements({})
    @Operation(summary = "Gửi lại OTP đăng ký")
    public APIResponse<OtpResponse> resend(@Valid @RequestBody EmailRequest request) {
        return registrationService.resend(request);
    }

    @PostMapping("/register/verify")
    @SecurityRequirements({})
    @Operation(summary = "Xác thực OTP đăng ký (bước 2: hoàn tất tạo tài khoản)")
    public APIResponse<UserResponse> verify(@Valid @RequestBody VerifyOtpRequest request) {
        return registrationService.verify(request);
    }

    @PostMapping("/verify-email")
    @SecurityRequirements({})
    @Operation(summary = "Xác thực email qua OTP (alias của register/verify)")
    public APIResponse<UserResponse> verifyEmail(@Valid @RequestBody VerifyOtpRequest request) {
        return registrationService.verify(request);
    }

    @PostMapping("/forgot-password")
    @SecurityRequirements({})
    @Operation(summary = "Quên mật khẩu: gửi OTP xác thực")
    public APIResponse<Void> forgotPassword(@Valid @RequestBody EmailRequest request) {
        return registrationService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    @SecurityRequirements({})
    @Operation(summary = "Đặt lại mật khẩu bằng mã OTP")
    public APIResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return registrationService.resetPassword(request);
    }

    @PostMapping("/login")
    @SecurityRequirements({})
    @Operation(summary = "Đăng nhập hợp nhất (Web: cookie; Mobile: body)")
    public APIResponse<AuthenticationResponse> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest,
                                                     HttpServletResponse httpResponse) {
        return authenticationService.login(request, httpRequest, httpResponse);
    }

    @PostMapping("/refresh")
    @SecurityRequirements({})
    @Operation(summary = "Làm mới access token và xoay vòng refresh token")
    public APIResponse<AuthenticationResponse> refresh(@RequestBody(required = false) RefreshRequest refreshRequest,
                                                       HttpServletRequest httpRequest,
                                                       HttpServletResponse httpResponse) {
        return authenticationService.refresh(refreshRequest, httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    @SecurityRequirements({})
    @Operation(summary = "Đăng xuất khỏi phiên hiện tại")
    public APIResponse<Void> logout(@RequestBody(required = false) RefreshRequest refreshRequest,
                                    HttpServletRequest httpRequest,
                                    HttpServletResponse httpResponse) {
        return authenticationService.logout(refreshRequest, httpRequest, httpResponse);
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Đăng xuất khỏi tất cả phiên đăng nhập")
    public APIResponse<Void> logoutAll(@CurrentUserId UUID actorId, HttpServletResponse response) {
        return authenticationService.logoutAll(actorId, response);
    }

    // ===== Deprecated Endpoints (Backward Compatibility) =====

    @Deprecated
    @PostMapping("/mobile/login")
    @SecurityRequirements({})
    @Operation(summary = "[Deprecated] Đăng nhập dành cho mobile cũ")
    public APIResponse<AuthenticationResponse> mobileLogin(@Valid @RequestBody LoginRequest request,
                                                           HttpServletResponse response) {
        if (response != null) {
            response.setHeader(TokenClaims.DEPRECATION_HEADER, "@deprecated since 2026-10-19; migrate to /api/v1/auth/login with X-Client-Type: mobile");
        }
        return authenticationService.loginMobile(request);
    }

    @Deprecated
    @PostMapping("/mobile/refresh")
    @SecurityRequirements({})
    @Operation(summary = "[Deprecated] Refresh token dành cho mobile cũ")
    public APIResponse<AuthenticationResponse> mobileRefresh(@Valid @RequestBody RefreshRequest request,
                                                             HttpServletResponse response) {
        if (response != null) {
            response.setHeader(TokenClaims.DEPRECATION_HEADER, "@deprecated since 2026-10-19; migrate to /api/v1/auth/refresh with X-Client-Type: mobile");
        }
        return authenticationService.refreshMobile(request);
    }

    @Deprecated
    @PostMapping("/mobile/logout")
    @SecurityRequirements({})
    @Operation(summary = "[Deprecated] Đăng xuất dành cho mobile cũ")
    public APIResponse<Void> mobileLogout(@Valid @RequestBody RefreshRequest refreshRequest,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        if (response != null) {
            response.setHeader(TokenClaims.DEPRECATION_HEADER, "@deprecated since 2026-10-19; migrate to /api/v1/auth/logout with X-Client-Type: mobile");
        }
        return authenticationService.logoutMobile(refreshRequest, request);
    }

    // =========================================================================
    // DELETE Endpoints
    // =========================================================================

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Hủy một phiên đăng nhập theo ID")
    public APIResponse<Void> revokeSession(@CurrentUserId UUID actorId,
                                           @PathVariable String sessionId) {
        return authenticationService.revokeSession(actorId, sessionId);
    }
}
