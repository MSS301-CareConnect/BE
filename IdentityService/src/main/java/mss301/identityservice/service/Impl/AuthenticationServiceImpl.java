package mss301.identityservice.service.Impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.config.IdentityProperties;
import mss301.identityservice.dto.request.LoginRequest;
import mss301.identityservice.dto.request.RefreshRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.AuthenticatedUser;
import mss301.identityservice.dto.response.AuthenticationResponse;
import mss301.identityservice.dto.response.CsrfResponse;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.enums.TokenType;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.mapper.AuthenticationMapper;
import mss301.identityservice.security.CookieUtils;
import mss301.identityservice.security.TokenClaims;
import mss301.identityservice.service.AccountService;
import mss301.identityservice.service.AuthenticationService;
import mss301.identityservice.service.CurrentIdentityService;
import mss301.identityservice.service.IdentityStateService;
import mss301.identityservice.service.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    static String LOGIN_SUCCESS = "Đăng nhập thành công";
    static String REFRESH_SUCCESS = "Làm mới phiên đăng nhập thành công";
    static String LOGOUT_SUCCESS = "Đăng xuất thành công";

    AccountService accountService;
    CurrentIdentityService currentIdentityService;
    IdentityStateService identityStateService;
    JwtService jwtService;
    IdentityProperties identityProperties;
    CookieUtils cookieUtils;
    AuthenticationMapper authenticationMapper;

    @Override
    public APIResponse<CsrfResponse> getCsrf(CsrfToken csrfToken) {
        CsrfResponse csrfResponse = authenticationMapper.toCsrfResponse(csrfToken);
        return APIResponse.success("Lấy CSRF token thành công", csrfResponse);
    }

    @Override
    public APIResponse<AuthenticationResponse> login(LoginRequest request, HttpServletResponse response) {
        AuthenticatedUser authenticatedUser = accountService.verifyCredentials(request.getEmail(), request.getPassword());
        return issueWebTokens(authenticatedUser.getUser(), authenticatedUser.getAuthzVersion(), response, LOGIN_SUCCESS);
    }

    @Override
    public APIResponse<AuthenticationResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        Jwt refreshToken = parseRefreshToken(CookieUtils.read(request, CookieUtils.REFRESH));
        UserResponse user = consumeRefreshToken(refreshToken);
        return issueWebTokens(user, jwtService.getAuthzVersion(refreshToken), response, REFRESH_SUCCESS);
    }

    @Override
    public APIResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        revokeRefreshToken(CookieUtils.read(request, CookieUtils.REFRESH));
        blacklistAccessToken(resolveAccessToken(request));
        cookieUtils.clear(response);
        return APIResponse.success(LOGOUT_SUCCESS);
    }

    @Override
    public APIResponse<AuthenticationResponse> loginMobile(LoginRequest request) {
        AuthenticatedUser authenticatedUser = accountService.verifyCredentials(request.getEmail(), request.getPassword());
        return issueMobileTokens(authenticatedUser.getUser(), authenticatedUser.getAuthzVersion(), LOGIN_SUCCESS);
    }

    @Override
    public APIResponse<AuthenticationResponse> refreshMobile(RefreshRequest request) {
        Jwt refreshToken = parseRefreshToken(request.getRefreshToken());
        UserResponse user = consumeRefreshToken(refreshToken);
        return issueMobileTokens(user, jwtService.getAuthzVersion(refreshToken), REFRESH_SUCCESS);
    }

    @Override
    public APIResponse<Void> logoutMobile(RefreshRequest refreshRequest, HttpServletRequest request) {
        revokeRefreshToken(refreshRequest.getRefreshToken());
        blacklistAccessToken(resolveAccessToken(request));
        return APIResponse.success(LOGOUT_SUCCESS);
    }

    @Override
    public APIResponse<Void> logoutAll(UUID actorId, HttpServletResponse response) {
        accountService.invalidateSessions(actorId);
        cookieUtils.clear(response);
        return APIResponse.success("Đã đăng xuất mọi phiên");
    }

    private APIResponse<AuthenticationResponse> issueWebTokens(UserResponse user, long authzVersion,
                                                               HttpServletResponse response, String message) {
        Jwt accessToken = jwtService.issue(user, authzVersion, TokenType.ACCESS);
        Jwt refreshToken = issueRefreshToken(user, authzVersion);
        String csrfToken = cookieUtils.issue(response, accessToken.getTokenValue(), refreshToken.getTokenValue());
        AuthenticationResponse authenticationResponse =
                authenticationMapper.toWebResponse(csrfToken, identityProperties.getAccessTtl().toSeconds());
        return APIResponse.success(message, authenticationResponse);
    }

    private APIResponse<AuthenticationResponse> issueMobileTokens(UserResponse user, long authzVersion, String message) {
        Jwt accessToken = jwtService.issue(user, authzVersion, TokenType.ACCESS);
        Jwt refreshToken = issueRefreshToken(user, authzVersion);
        AuthenticationResponse authenticationResponse = authenticationMapper.toMobileResponse(
                accessToken.getTokenValue(), refreshToken.getTokenValue(), identityProperties.getAccessTtl().toSeconds());
        return APIResponse.success(message, authenticationResponse);
    }

    private Jwt issueRefreshToken(UserResponse user, long authzVersion) {
        Jwt refreshToken = jwtService.issue(user, authzVersion, TokenType.REFRESH);
        // Lưu jti trước khi trả token: Redis lỗi → 503, không đăng nhập "nửa vời".
        identityStateService.storeRefresh(refreshToken.getId(), user.getId(), identityProperties.getRefreshTtl());
        return refreshToken;
    }

    private Jwt parseRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return jwtService.parseRefresh(token);
    }

    // Kiểm tài khoản còn hợp lệ rồi mới claim jti (nguyên tử): chỉ một request refresh đồng thời thành công.
    private UserResponse consumeRefreshToken(Jwt refreshToken) {
        UUID userId = jwtService.getUserId(refreshToken);
        UserResponse user;
        try {
            user = currentIdentityService.getActiveUser(userId, jwtService.getAuthzVersion(refreshToken));
        } catch (AuthenticationException e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (!identityStateService.consumeRefresh(refreshToken.getId(), userId)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }

    // Token sai/hết hạn vẫn cho logout thành công; chỉ lỗi hạ tầng (Redis) mới được ném ra.
    private void revokeRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            Jwt refreshToken = jwtService.parseRefresh(token);
            identityStateService.consumeRefresh(refreshToken.getId(), jwtService.getUserId(refreshToken));
        } catch (AppException e) {
            rethrowUnlessUnauthenticated(e);
        }
    }

    private void blacklistAccessToken(String token) {
        if (token == null) {
            return;
        }
        try {
            Jwt accessToken = jwtService.parseAccess(token);
            identityStateService.blacklist(accessToken.getId(), Duration.between(Instant.now(), accessToken.getExpiresAt()));
        } catch (AppException e) {
            rethrowUnlessUnauthenticated(e);
        }
    }

    private void rethrowUnlessUnauthenticated(AppException exception) {
        if (exception.getErrorCode() != ErrorCode.UNAUTHENTICATED) {
            throw exception;
        }
    }

    // Ưu tiên cookie (web), sau đó header Authorization (mobile).
    private String resolveAccessToken(HttpServletRequest request) {
        String cookieToken = CookieUtils.read(request, CookieUtils.ACCESS);
        if (cookieToken != null) {
            return cookieToken;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(TokenClaims.BEARER_PREFIX)) {
            return header.substring(TokenClaims.BEARER_PREFIX.length());
        }
        return null;
    }
}
