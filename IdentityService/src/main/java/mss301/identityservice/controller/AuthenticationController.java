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
import mss301.identityservice.dto.request.LogoutRequest;
import mss301.identityservice.dto.request.RefreshRequest;
import mss301.identityservice.dto.request.RegisterRequest;
import mss301.identityservice.dto.request.ResetPasswordRequest;
import mss301.identityservice.dto.request.ServiceTokenRequest;
import mss301.identityservice.dto.request.VerifyOtpRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.AuthenticationResponse;
import mss301.identityservice.dto.response.CsrfResponse;
import mss301.identityservice.dto.response.OtpResponse;
import mss301.identityservice.dto.response.ServiceTokenResponse;
import mss301.identityservice.dto.response.SessionResponse;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.security.CurrentUserId;
import mss301.identityservice.service.AuthenticationService;
import mss301.identityservice.service.RegistrationService;
import mss301.identityservice.service.ServiceClientService;
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
    ServiceClientService serviceClientService;

    // =========================================================================
    // GET Endpoints
    // =========================================================================

    @GetMapping("/csrf")
    @SecurityRequirements({})
    public APIResponse<CsrfResponse> getCsrf(CsrfToken csrfToken) {
        return authenticationService.getCsrf(csrfToken);
    }

    @GetMapping("/me")
    public APIResponse<UserResponse> me(@CurrentUserId UUID actorId) {
        return authenticationService.me(actorId);
    }

    @GetMapping("/sessions")
    public APIResponse<List<SessionResponse>> listSessions(@CurrentUserId UUID actorId,
                                                       HttpServletRequest httpRequest) {
        return authenticationService.listSessions(actorId, httpRequest);
    }

    // =========================================================================
    // POST Endpoints
    // =========================================================================

    @PostMapping("/register")
    @SecurityRequirements({})
    public APIResponse<OtpResponse> register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/register/resend-otp")
    @SecurityRequirements({})
    public APIResponse<OtpResponse> resendOtp(@Valid @RequestBody EmailRequest request) {
        return registrationService.resendOtp(request);
    }

    @PostMapping("/register/verify")
    @SecurityRequirements({})
    public APIResponse<UserResponse> verify(@Valid @RequestBody VerifyOtpRequest request) {
        return registrationService.verify(request);
    }

    @PostMapping("/verify-email")
    @SecurityRequirements({})
    public APIResponse<UserResponse> verifyEmail(@Valid @RequestBody VerifyOtpRequest request) {
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

    @PostMapping("/login")
    @SecurityRequirements({})
    public APIResponse<AuthenticationResponse> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest,
                                                     HttpServletResponse httpResponse) {
        return authenticationService.login(request, httpRequest, httpResponse);
    }

    @PostMapping("/refresh")
    @SecurityRequirements({})
    public APIResponse<AuthenticationResponse> refresh(@RequestBody(required = false) RefreshRequest refreshRequest,
                                                       HttpServletRequest httpRequest,
                                                       HttpServletResponse httpResponse) {
        return authenticationService.refresh(refreshRequest, httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    @SecurityRequirements({})
    public APIResponse<Void> logout(@RequestBody(required = false) LogoutRequest logoutRequest,
                                    HttpServletRequest httpRequest,
                                    HttpServletResponse httpResponse) {
        return authenticationService.logout(logoutRequest, httpRequest, httpResponse);
    }

    @PostMapping("/service-token")
    @SecurityRequirements({})
    public APIResponse<ServiceTokenResponse> issueServiceToken(@Valid @RequestBody ServiceTokenRequest request,
                                                               HttpServletRequest httpServletRequest) {
        return serviceClientService.issueToken(request, httpServletRequest);
    }

    // ===== Deprecated Endpoints (Backward Compatibility) =====

    @Deprecated
    @PostMapping("/mobile/login")
    @SecurityRequirements({})
    public APIResponse<AuthenticationResponse> loginMobile(@Valid @RequestBody LoginRequest request,
                                                           HttpServletResponse httpResponse) {
        return authenticationService.loginMobile(request, httpResponse);
    }

    @Deprecated
    @PostMapping("/mobile/refresh")
    @SecurityRequirements({})
    public APIResponse<AuthenticationResponse> refreshMobile(@Valid @RequestBody RefreshRequest request,
                                                             HttpServletResponse httpResponse) {
        return authenticationService.refreshMobile(request, httpResponse);
    }

    // =========================================================================
    // DELETE Endpoints
    // =========================================================================

    @DeleteMapping("/sessions/{sessionId}")
    public APIResponse<Void> revokeSession(@CurrentUserId UUID actorId,
                                           @PathVariable String sessionId) {
        return authenticationService.revokeSession(actorId, sessionId);
    }
}
