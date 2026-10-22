package mss301.identityservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.AuthenticationResponse;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.enums.ClientType;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TokenTransportImpl implements TokenTransport {

    CookieUtils cookieUtils;

    @Override
    public ClientType resolveClientType(HttpServletRequest request) {
        if (request == null) {
            return ClientType.MOBILE;
        }
        String header = request.getHeader(TokenClaims.CLIENT_TYPE_HEADER);
        if (header != null && "web".equalsIgnoreCase(header.trim())) {
            return ClientType.WEB;
        }
        return ClientType.MOBILE;
    }

    @Override
    public APIResponse<AuthenticationResponse> deliverTokens(ClientType clientType, UserResponse user,
                                                             String accessToken, String refreshToken,
                                                             long accessExpiresInSeconds,
                                                             HttpServletResponse response, String message) {
        if (clientType == ClientType.WEB) {
            String csrfToken = cookieUtils.issue(response, accessToken, refreshToken);
            AuthenticationResponse result = AuthenticationResponse.builder()
                    .authenticated(true)
                    .csrfToken(csrfToken)
                    .expiresInSeconds(accessExpiresInSeconds)
                    .user(user)
                    .build();
            return APIResponse.success(message, result);
        } else {
            AuthenticationResponse result = AuthenticationResponse.builder()
                    .authenticated(true)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresInSeconds(accessExpiresInSeconds)
                    .user(user)
                    .build();
            return APIResponse.success(message, result);
        }
    }

    @Override
    public String extractRefreshToken(ClientType clientType, String bodyRefreshToken, HttpServletRequest request) {
        if (clientType == ClientType.WEB) {
            String cookieToken = CookieUtils.read(request, CookieUtils.REFRESH);
            if (cookieToken != null && !cookieToken.isBlank()) {
                return cookieToken.trim();
            }
        }
        if (bodyRefreshToken != null && !bodyRefreshToken.isBlank()) {
            return bodyRefreshToken.trim();
        }
        String header = request.getHeader("X-Refresh-Token");
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        return null;
    }

    @Override
    public String resolveAccessToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(TokenClaims.BEARER_PREFIX)) {
            return header.substring(TokenClaims.BEARER_PREFIX.length()).trim();
        }
        String cookieToken = CookieUtils.read(request, CookieUtils.ACCESS);
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken.trim();
        }
        return null;
    }

    @Override
    public void clearTokens(ClientType clientType, HttpServletResponse response) {
        if (clientType == ClientType.WEB && response != null) {
            cookieUtils.clear(response);
        }
    }
}
