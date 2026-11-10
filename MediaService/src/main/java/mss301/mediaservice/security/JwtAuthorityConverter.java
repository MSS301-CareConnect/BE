package mss301.mediaservice.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Access token → Authentication: roles[] → ROLE_<code>, permissions[] → giữ nguyên mã. Tên authentication = sub
 * (userId, đọc bằng {@link CurrentUserId}). Quyền là snapshot trong token (≤ access TTL) — service này không nạp lại
 * trạng thái tài khoản như IdentityService.
 */
@Component
public class JwtAuthorityConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        for (String role : readList(jwt, TokenClaims.ROLES)) {
            authorities.add(new SimpleGrantedAuthority(TokenClaims.ROLE_AUTHORITY_PREFIX + role));
        }
        for (String permission : readList(jwt, TokenClaims.PERMISSIONS)) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private List<String> readList(Jwt jwt, String claim) {
        List<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? List.of() : values;
    }
}

