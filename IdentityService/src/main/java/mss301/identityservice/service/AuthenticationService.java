package mss301.identityservice.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mss301.identityservice.dto.request.LoginRequest;
import mss301.identityservice.dto.request.RefreshRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.AuthenticationResponse;
import mss301.identityservice.dto.response.CsrfResponse;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.UUID;

public interface AuthenticationService {
    APIResponse<CsrfResponse> getCsrf(CsrfToken csrfToken);

    // Web: token qua HttpOnly cookie
    APIResponse<AuthenticationResponse> login(LoginRequest request, HttpServletResponse response);

    APIResponse<AuthenticationResponse> refresh(HttpServletRequest request, HttpServletResponse response);

    APIResponse<Void> logout(HttpServletRequest request, HttpServletResponse response);

    // Mobile: token trong body / header Authorization
    APIResponse<AuthenticationResponse> loginMobile(LoginRequest request);

    APIResponse<AuthenticationResponse> refreshMobile(RefreshRequest request);

    APIResponse<Void> logoutMobile(RefreshRequest refreshRequest, HttpServletRequest request);

    APIResponse<Void> logoutAll(UUID actorId, HttpServletResponse response);
}
