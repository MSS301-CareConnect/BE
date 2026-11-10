package mss301.identityservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.AuthenticationResponse;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.enums.ClientType;

public interface TokenTransport {
    ClientType resolveClientType(HttpServletRequest request);

    APIResponse<AuthenticationResponse> deliverTokens(ClientType clientType, UserResponse user,
                                                     String accessToken, String refreshToken,
                                                     long accessExpiresInSeconds,
                                                     HttpServletResponse response, String message);

    String extractRefreshToken(ClientType clientType, String bodyRefreshToken, HttpServletRequest request);

    String resolveAccessToken(HttpServletRequest request);

    void clearTokens(ClientType clientType, HttpServletResponse response);
}
