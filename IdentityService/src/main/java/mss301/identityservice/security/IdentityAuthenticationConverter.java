package mss301.identityservice.security;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.enums.RoleCode;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.service.CurrentIdentityService;
import mss301.identityservice.service.SessionStore;
import mss301.identityservice.config.RedisKeys;
import java.util.Map;
import mss301.identityservice.service.JwtService;
import mss301.identityservice.service.ServiceClientService;
import mss301.identityservice.service.TokenStore;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Access token (đã qua decoder) → Authentication với quyền HIỆN HÀNH từ DB, không tin roles/permissions
 * trong JWT: khóa tài khoản / thu hồi quyền có hiệu lực ngay tại IdentityService.
 * Tên authentication = userId (đọc bằng {@link CurrentUserId}).
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IdentityAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    SessionStore sessionStore;
    TokenStore tokenStore;
    CurrentIdentityService currentIdentityService;
    JwtService jwtService;
    ServiceClientService serviceClientService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            if (jwt.getId() == null || tokenStore.isAccessTokenBlacklisted(jwt.getId())) {
                throw new BadCredentialsException("Revoked token");
            }
            if (jwt.hasClaim(TokenClaims.CLIENT_ID)) {
                return toServiceAuthentication(jwt);
            }
            String sid = jwtService.getSessionId(jwt);
            if (sid != null) {
                Map<String, String> session = sessionStore.getSession(sid);
                if (!jwt.getSubject().equals(session.get(RedisKeys.FIELD_USER_ID))) {
                    throw new BadCredentialsException("Revoked session");
                }
            }
            UserResponse user = currentIdentityService.getActiveUser(jwtService.getUserId(jwt), jwtService.getAuthzVersion(jwt));
            return new JwtAuthenticationToken(jwt, toAuthorities(user), user.getId().toString());
        } catch (AppException | DataAccessException e) {
            // Redis/DB lỗi → 503 qua SecurityErrorHandler, không bao giờ bypass hay giả làm token sai.
            throw new InternalAuthenticationServiceException("Identity dependency unavailable", e);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Invalid subject", e);
        }
    }

    // Token SERVICE: không tra bảng users; client phải còn active. Quyền duy nhất là ROLE_SERVICE.
    private AbstractAuthenticationToken toServiceAuthentication(Jwt jwt) {
        UUID clientUuid = UUID.fromString(jwt.getSubject());
        if (!serviceClientService.isActive(clientUuid, jwt.getClaimAsString(TokenClaims.CLIENT_ID))) {
            throw new BadCredentialsException("Service client disabled");
        }
        Set<SimpleGrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority(TokenClaims.ROLE_AUTHORITY_PREFIX + RoleCode.SERVICE.name()));
        return new JwtAuthenticationToken(jwt, authorities, clientUuid.toString());
    }

    private Set<SimpleGrantedAuthority> toAuthorities(UserResponse user) {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        for (String role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority(TokenClaims.ROLE_AUTHORITY_PREFIX + role));
        }
        for (String permission : user.getPermissions()) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        return authorities;
    }
}
