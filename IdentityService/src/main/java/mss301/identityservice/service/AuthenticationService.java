package mss301.identityservice.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mss301.identityservice.dto.request.LoginRequest;
import mss301.identityservice.dto.request.RefreshRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.AuthenticationResponse;
import mss301.identityservice.dto.response.CsrfResponse;
import mss301.identityservice.dto.response.SessionResponse;
import mss301.identityservice.dto.response.UserResponse;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.List;
import java.util.UUID;

public interface AuthenticationService {
    APIResponse<CsrfResponse> getCsrf(CsrfToken csrfToken);

    // Unified auth methods
    APIResponse<AuthenticationResponse> login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    APIResponse<AuthenticationResponse> refresh(RefreshRequest refreshRequest, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    APIResponse<Void> logout(RefreshRequest refreshRequest, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    APIResponse<Void> logoutAll(UUID actorId, HttpServletResponse response);

    APIResponse<UserResponse> me(UUID actorId);

    APIResponse<List<SessionResponse>> listSessions(UUID actorId, HttpServletRequest httpRequest);

    APIResponse<Void> revokeSession(UUID actorId, String sessionId);

    // Deprecated methods for backward compatibility
    @Deprecated
    APIResponse<AuthenticationResponse> loginMobile(LoginRequest request);

    @Deprecated
    APIResponse<AuthenticationResponse> refreshMobile(RefreshRequest request);

    @Deprecated
    APIResponse<Void> logoutMobile(RefreshRequest refreshRequest, HttpServletRequest request);
}
