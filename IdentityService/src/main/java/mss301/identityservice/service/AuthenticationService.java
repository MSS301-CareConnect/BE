package mss301.identityservice.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mss301.identityservice.dto.request.LoginRequest;
import mss301.identityservice.dto.request.LogoutRequest;
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
    // Lấy CSRF token cho web.
    APIResponse<CsrfResponse> getCsrf(CsrfToken csrfToken);

    // Unified auth methods
    APIResponse<AuthenticationResponse> login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    // Làm mới access token bằng refresh token.
    APIResponse<AuthenticationResponse> refresh(RefreshRequest refreshRequest, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    // allSessions = true → đăng xuất mọi phiên của tài khoản (gộp /auth/logout-all, 2026-10-22).
    APIResponse<Void> logout(LogoutRequest logoutRequest, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    // Thông tin người dùng đang đăng nhập.
    APIResponse<UserResponse> me(UUID actorId);

    // Danh sách phiên đăng nhập của người dùng.
    APIResponse<List<SessionResponse>> listSessions(UUID actorId, HttpServletRequest httpRequest);

    // Thu hồi một phiên đăng nhập.
    APIResponse<Void> revokeSession(UUID actorId, String sessionId);

    // Deprecated methods for backward compatibility
    // httpResponse chỉ dùng để gắn header Deprecation — không đặt cookie (mobile nhận token trong body).
    @Deprecated
    APIResponse<AuthenticationResponse> loginMobile(LoginRequest request, HttpServletResponse httpResponse);

    // Làm mới token cho mobile (endpoint cũ).
    @Deprecated
    APIResponse<AuthenticationResponse> refreshMobile(RefreshRequest request, HttpServletResponse httpResponse);
}
