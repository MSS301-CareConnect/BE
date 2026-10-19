package mss301.identityservice.controller;

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
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.security.CurrentUserId;
import mss301.identityservice.service.AuthenticationService;
import mss301.identityservice.service.RegistrationService;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Authentication", description = "Xác thực, đăng ký, OTP, mật khẩu. Web dùng HttpOnly cookie + CSRF; mobile dùng token trong body + Bearer header.")
public class AuthenticationController {
    AuthenticationService authenticationService;
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

    // ===== Web =====

    @GetMapping("/csrf")
    @SecurityRequirements({})
    public APIResponse<CsrfResponse> csrf(CsrfToken csrfToken) {
        return authenticationService.getCsrf(csrfToken);
    }

    @PostMapping("/login")
    @SecurityRequirements({})
    public APIResponse<AuthenticationResponse> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletResponse response) {
        return authenticationService.login(request, response);
    }

    @PostMapping("/refresh")
    @SecurityRequirements({})
    public APIResponse<AuthenticationResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        return authenticationService.refresh(request, response);
    }

    @PostMapping("/logout")
    @SecurityRequirements({})
    public APIResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        return authenticationService.logout(request, response);
    }

    // ===== Mobile =====

    @PostMapping("/mobile/login")
    @SecurityRequirements({})
    public APIResponse<AuthenticationResponse> mobileLogin(@Valid @RequestBody LoginRequest request) {
        return authenticationService.loginMobile(request);
    }

    @PostMapping("/mobile/refresh")
    @SecurityRequirements({})
    public APIResponse<AuthenticationResponse> mobileRefresh(@Valid @RequestBody RefreshRequest request) {
        return authenticationService.refreshMobile(request);
    }

    @PostMapping("/mobile/logout")
    @SecurityRequirements({})
    public APIResponse<Void> mobileLogout(@Valid @RequestBody RefreshRequest refreshRequest,
                                          HttpServletRequest request) {
        return authenticationService.logoutMobile(refreshRequest, request);
    }

    // ===== Chung =====

    @PostMapping("/logout-all")
    public APIResponse<Void> logoutAll(@CurrentUserId UUID actorId, HttpServletResponse response) {
        return authenticationService.logoutAll(actorId, response);
    }
}
