package mss301.identityservice.service.Impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.config.IdentityProperties;
import mss301.identityservice.config.RedisKeys;
import mss301.identityservice.dto.request.LoginRequest;
import mss301.identityservice.dto.request.LogoutRequest;
import mss301.identityservice.dto.request.RefreshRequest;
import mss301.identityservice.dto.response.*;
import mss301.identityservice.enums.ClientType;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.mapper.AuthenticationMapper;
import mss301.identityservice.security.TokenClaims;
import mss301.identityservice.security.TokenTransport;
import mss301.identityservice.service.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    static String LOGIN_SUCCESS = "Đăng nhập thành công";
    static String REFRESH_SUCCESS = "Làm mới phiên đăng nhập thành công";
    static String LOGOUT_SUCCESS = "Đăng xuất thành công";
    static String LOGOUT_ALL_SUCCESS = "Đã đăng xuất mọi phiên";
    static String DEPRECATION_PREFIX = "@deprecated since 2026-10-19; migrate to ";
    static String DEPRECATION_SUFFIX = " with X-Client-Type: mobile";
    static String LOGIN_REPLACEMENT_PATH = "/api/v1/auth/login";
    static String REFRESH_REPLACEMENT_PATH = "/api/v1/auth/refresh";

    UserService userService;
    CurrentIdentityService currentIdentityService;
    IdentityStateService identityStateService;
    TokenStore tokenStore;
    SessionStore sessionStore;
    RateLimiter rateLimiter;
    JwtService jwtService;
    IdentityProperties identityProperties;
    TokenTransport tokenTransport;
    AuthenticationMapper authenticationMapper;

    @Override
    public APIResponse<CsrfResponse> getCsrf(CsrfToken csrfToken) {
        CsrfResponse csrfResponse = authenticationMapper.toCsrfResponse(csrfToken);
        return APIResponse.success("Lấy CSRF token thành công", csrfResponse);
    }

    @Override
    public APIResponse<AuthenticationResponse> login(LoginRequest request, HttpServletRequest httpRequest,
                                                     HttpServletResponse httpResponse) {
        String ip = extractIp(httpRequest);
        rateLimiter.checkLoginRateLimit(request.getEmail(), ip);

        AuthenticatedUser authenticatedUser;
        try {
            authenticatedUser = userService.verifyCredentials(request.getEmail(), request.getPassword());
        } catch (AppException e) {
            if (e.getErrorCode() == ErrorCode.INVALID_CREDENTIALS) {
                rateLimiter.recordLoginFailure(request.getEmail(), ip);
            }
            throw e;
        }
        rateLimiter.resetLoginAttempts(request.getEmail(), ip);

        ClientType clientType = tokenTransport.resolveClientType(httpRequest);
        String deviceId = httpRequest != null ? httpRequest.getHeader(TokenClaims.DEVICE_ID_HEADER) : null;
        String userAgent = httpRequest != null ? httpRequest.getHeader(HttpHeaders.USER_AGENT) : null;

        return issueSessionAndTokens(authenticatedUser.getUser(), authenticatedUser.getAuthzVersion(),
                null, clientType, deviceId, userAgent, ip, httpResponse, LOGIN_SUCCESS);
    }

    @Override
    public APIResponse<AuthenticationResponse> refresh(RefreshRequest refreshRequest, HttpServletRequest httpRequest,
                                                       HttpServletResponse httpResponse) {
        ClientType clientType = tokenTransport.resolveClientType(httpRequest);
        String bodyRefreshToken = refreshRequest != null ? refreshRequest.getRefreshToken() : null;
        String rawRefreshToken = tokenTransport.extractRefreshToken(clientType, bodyRefreshToken, httpRequest);
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String tokenHash = jwtService.hashToken(rawRefreshToken);
        Map<String, String> tokenData = tokenStore.getRefreshToken(tokenHash);

        if (tokenData.isEmpty()) {
            // Kiểm tra Token Reuse: nếu token này từng tồn tại và đã bị thu hồi trước đó
            if (tokenStore.isTokenRevoked(tokenHash)) {
                log.warn("Refresh token reuse detected for tokenHash={}. Revoking all user sessions.", tokenHash);
                // Token bị dùng lại: ném UNAUTHENTICATED để từ chối truy cập
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        UUID userId = UUID.fromString(tokenData.get(RedisKeys.FIELD_USER_ID));
        String sessionId = tokenData.get(RedisKeys.FIELD_SESSION_ID);

        // Kiểm tra session còn tồn tại trong Redis
        Map<String, String> sessionData = sessionStore.getSession(sessionId);
        if (sessionData.isEmpty()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // Kiểm tra tài khoản còn ACTIVE trong DB
        AuthenticatedUser authenticatedUser;
        try {
            authenticatedUser = currentIdentityService.getActiveAuthenticatedUser(userId);
        } catch (AuthenticationException e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // Thu hồi token cũ và đánh dấu đã thu hồi để chống reuse
        tokenStore.revokeRefreshToken(tokenHash, userId);
        tokenStore.recordRevokedToken(tokenHash, userId, identityProperties.getRefreshTtl());

        // Cấp phát token mới trên cùng một sessionId (Refresh Token Rotation)
        String ip = extractIp(httpRequest);
        String deviceId = httpRequest != null ? httpRequest.getHeader(TokenClaims.DEVICE_ID_HEADER)
                : sessionData.get(RedisKeys.FIELD_DEVICE_ID);
        String userAgent = httpRequest != null ? httpRequest.getHeader(HttpHeaders.USER_AGENT)
                : sessionData.get(RedisKeys.FIELD_USER_AGENT);

        return issueSessionAndTokens(authenticatedUser.getUser(), authenticatedUser.getAuthzVersion(),
                sessionId, clientType, deviceId, userAgent, ip, httpResponse, REFRESH_SUCCESS);
    }

    @Override
    public APIResponse<Void> logout(LogoutRequest logoutRequest, HttpServletRequest httpRequest,
                                    HttpServletResponse httpResponse) {
        ClientType clientType = tokenTransport.resolveClientType(httpRequest);
        String bodyRefreshToken = logoutRequest != null ? logoutRequest.getRefreshToken() : null;
        String rawRefreshToken = tokenTransport.extractRefreshToken(clientType, bodyRefreshToken, httpRequest);
        String rawAccessToken = tokenTransport.resolveAccessToken(httpRequest);
        boolean allSessions = logoutRequest != null && Boolean.TRUE.equals(logoutRequest.getAllSessions());

        // Phải xác định chủ tài khoản TRƯỚC khi thu hồi token hiện tại — sau đó dữ liệu refresh trên Redis không còn.
        UUID ownerId = allSessions ? findSessionOwner(rawRefreshToken, rawAccessToken) : null;

        revokeRefreshToken(rawRefreshToken);
        blacklistAccessToken(rawAccessToken);

        String message = LOGOUT_SUCCESS;
        if (ownerId != null) {
            // Thu hồi mọi session/refresh trên Redis + tăng authzVersion để mọi access token cũ mất hiệu lực
            sessionStore.removeAllUserSessions(ownerId);
            userService.invalidateSessions(ownerId);
            message = LOGOUT_ALL_SUCCESS;
        }

        tokenTransport.clearTokens(clientType, httpResponse);
        return APIResponse.success(message);
    }

    @Override
    public APIResponse<UserResponse> me(UUID actorId) {
        return userService.me(actorId);
    }

    @Override
    public APIResponse<List<SessionResponse>> listSessions(UUID actorId, HttpServletRequest httpRequest) {
        String currentSid = null;
        String rawAccessToken = tokenTransport.resolveAccessToken(httpRequest);
        if (rawAccessToken != null) {
            try {
                Jwt jwt = jwtService.parseAccess(rawAccessToken);
                currentSid = jwtService.getSessionId(jwt);
            } catch (AppException ignored) {
            }
        }

        Set<String> sessionIds = sessionStore.getUserSessionIds(actorId);
        List<SessionResponse> result = new ArrayList<>();

        for (String sid : sessionIds) {
            Map<String, String> sData = sessionStore.getSession(sid);
            if (sData.isEmpty()) {
                continue;
            }
            Instant createdAt = sData.containsKey(RedisKeys.FIELD_CREATED_AT)
                    ? Instant.parse(sData.get(RedisKeys.FIELD_CREATED_AT))
                    : Instant.now();
            Instant lastActiveAt = sData.containsKey(RedisKeys.FIELD_LAST_ACTIVE_AT)
                    ? Instant.parse(sData.get(RedisKeys.FIELD_LAST_ACTIVE_AT))
                    : createdAt;

            result.add(SessionResponse.builder()
                    .sessionId(sid)
                    .deviceId(sData.get(RedisKeys.FIELD_DEVICE_ID))
                    .userAgent(sData.get(RedisKeys.FIELD_USER_AGENT))
                    .ip(sData.get(RedisKeys.FIELD_IP))
                    .createdAt(createdAt)
                    .lastActiveAt(lastActiveAt)
                    .current(sid.equals(currentSid))
                    .build());
        }

        result.sort(Comparator.comparing(SessionResponse::getLastActiveAt).reversed());
        return APIResponse.success("Lấy danh sách phiên đăng nhập thành công", result);
    }

    @Override
    public APIResponse<Void> revokeSession(UUID actorId, String sessionId) {
        Map<String, String> sessionData = sessionStore.getSession(sessionId);
        if (sessionData.isEmpty()) {
            throw new AppException(ErrorCode.SESSION_NOT_FOUND);
        }
        String sessionUserId = sessionData.get(RedisKeys.FIELD_USER_ID);
        if (!actorId.toString().equals(sessionUserId)) {
            throw new AppException(ErrorCode.SESSION_ACCESS_DENIED);
        }

        String tokenHash = sessionData.get(RedisKeys.FIELD_REFRESH_TOKEN_HASH);
        if (tokenHash != null) {
            tokenStore.revokeRefreshToken(tokenHash, actorId);
        }
        sessionStore.removeSession(actorId, sessionId);
        return APIResponse.success("Xóa phiên đăng nhập thành công");
    }

    // ===== Deprecated methods =====

    @Override
    @Deprecated
    public APIResponse<AuthenticationResponse> loginMobile(LoginRequest request, HttpServletResponse httpResponse) {
        markDeprecated(httpResponse, LOGIN_REPLACEMENT_PATH);
        return login(request, null, null);
    }

    @Override
    @Deprecated
    public APIResponse<AuthenticationResponse> refreshMobile(RefreshRequest request, HttpServletResponse httpResponse) {
        markDeprecated(httpResponse, REFRESH_REPLACEMENT_PATH);
        return refresh(request, null, null);
    }

    // ===== Private helpers =====

    // /auth/mobile/* cũ: báo client chuyển sang endpoint hợp nhất kèm X-Client-Type: mobile (từ 2026-10-19).
    private void markDeprecated(HttpServletResponse httpResponse, String replacementPath) {
        if (httpResponse == null) {
            return;
        }
        httpResponse.setHeader(TokenClaims.DEPRECATION_HEADER,
                DEPRECATION_PREFIX + replacementPath + DEPRECATION_SUFFIX);
    }

    private APIResponse<AuthenticationResponse> issueSessionAndTokens(
            UserResponse user, long authzVersion, String existingSessionId,
            ClientType clientType, String deviceId, String userAgent, String ip,
            HttpServletResponse response, String message) {

        String sessionId = existingSessionId != null ? existingSessionId : UUID.randomUUID().toString();
        String rawRefreshToken = jwtService.issueOpaqueRefreshToken();
        String tokenHash = jwtService.hashToken(rawRefreshToken);
        Duration refreshTtl = identityProperties.getRefreshTtl();

        if (existingSessionId == null) {
            sessionStore.createSession(sessionId, user.getId(), deviceId, userAgent, ip, tokenHash, refreshTtl);
        } else {
            sessionStore.updateSessionActivity(sessionId, tokenHash, refreshTtl);
        }

        tokenStore.storeRefreshToken(tokenHash, user.getId(), sessionId, deviceId, userAgent, ip, refreshTtl);

        Jwt accessToken = jwtService.issueAccess(user, authzVersion, sessionId);

        return tokenTransport.deliverTokens(clientType, user, accessToken.getTokenValue(),
                rawRefreshToken, identityProperties.getAccessTtl().toSeconds(), response, message);
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    // Đăng xuất mọi phiên làm giảm quyền cả tài khoản (endpoint public) → chỉ nhận refresh còn trên Redis hoặc
    // access token còn hiệu lực đầy đủ (chưa blacklist, user ACTIVE, authzVersion khớp) như /logout-all cũ.
    private UUID findSessionOwner(String rawRefreshToken, String rawAccessToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            Map<String, String> tokenData = tokenStore.getRefreshToken(jwtService.hashToken(rawRefreshToken));
            if (!tokenData.isEmpty()) {
                return UUID.fromString(tokenData.get(RedisKeys.FIELD_USER_ID));
            }
        }
        if (rawAccessToken != null) {
            try {
                Jwt accessToken = jwtService.parseAccess(rawAccessToken);
                String jti = accessToken.getId();
                if (jti != null && !identityStateService.isBlacklisted(jti) && !tokenStore.isAccessTokenBlacklisted(jti)) {
                    UUID userId = jwtService.getUserId(accessToken);
                    currentIdentityService.getActiveUser(userId, jwtService.getAuthzVersion(accessToken));
                    return userId;
                }
            } catch (AppException e) {
                rethrowUnlessUnauthenticated(e);
            } catch (AuthenticationException e) {
                log.debug("Access token không còn hiệu lực khi đăng xuất mọi phiên");
            }
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    private void revokeRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        try {
            try {
                Jwt jwtRefresh = jwtService.parseRefresh(rawRefreshToken);
                identityStateService.consumeRefresh(jwtRefresh.getId(), jwtService.getUserId(jwtRefresh));
            } catch (Exception ignored) {
            }

            String tokenHash = jwtService.hashToken(rawRefreshToken);
            Map<String, String> tokenData = tokenStore.getRefreshToken(tokenHash);
            if (!tokenData.isEmpty()) {
                UUID userId = UUID.fromString(tokenData.get(RedisKeys.FIELD_USER_ID));
                String sessionId = tokenData.get(RedisKeys.FIELD_SESSION_ID);
                tokenStore.revokeRefreshToken(tokenHash, userId);
                sessionStore.removeSession(userId, sessionId);
            }
        } catch (AppException e) {
            // Token lỗi/không hợp lệ vẫn cho logout thành công (idempotent)
            rethrowUnlessUnauthenticated(e);
        }
    }

    private void blacklistAccessToken(String rawAccessToken) {
        if (rawAccessToken == null) {
            return;
        }
        try {
            Jwt accessToken = jwtService.parseAccess(rawAccessToken);
            if (accessToken.getExpiresAt() != null) {
                Duration remaining = Duration.between(Instant.now(), accessToken.getExpiresAt());
                tokenStore.blacklistAccessToken(accessToken.getId(), remaining);
                identityStateService.blacklist(accessToken.getId(), remaining);
            }
        } catch (AppException e) {
            rethrowUnlessUnauthenticated(e);
        }
    }

    private void rethrowUnlessUnauthenticated(AppException exception) {
        if (exception.getErrorCode() != ErrorCode.UNAUTHENTICATED) {
            throw exception;
        }
    }
}
