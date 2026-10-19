package mss301.identityservice.security;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.service.CurrentIdentityService;
import mss301.identityservice.service.IdentityStateService;
import mss301.identityservice.service.JwtService;
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

/**
 * Access token (đã qua decoder) → Authentication với quyền HIỆN HÀNH từ DB, không tin roles/permissions
 * trong JWT: khóa tài khoản / thu hồi quyền có hiệu lực ngay tại IdentityService.
 * Tên authentication = userId (đọc bằng {@link CurrentUserId}).
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IdentityAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    IdentityStateService identityStateService;
    TokenStore tokenStore;
    CurrentIdentityService currentIdentityService;
    JwtService jwtService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            if (jwt.getId() == null || identityStateService.isBlacklisted(jwt.getId()) || tokenStore.isAccessTokenBlacklisted(jwt.getId())) {
                throw new BadCredentialsException("Revoked token");
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
