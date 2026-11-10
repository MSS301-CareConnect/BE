package mss301.mediaservice.security;

import mss301.mediaservice.exception.AppException;
import mss301.mediaservice.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentRequestIdentity {
    public UUID userId() {
        return UUID.fromString(authentication().getName());
    }

    public boolean hasRole(String role) {
        return authentication().getAuthorities().stream()
                .anyMatch(authority -> (TokenClaims.ROLE_AUTHORITY_PREFIX + role).equals(authority.getAuthority()));
    }

    public String clientId() {
        Authentication authentication = authentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            String clientId = jwtAuthenticationToken.getToken().getClaimAsString(TokenClaims.CLIENT_ID);
            if (clientId != null && !clientId.isBlank() && hasRole("SERVICE")) {
                return clientId;
            }
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    private Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return authentication;
    }
}
