package mss301.apigateway.security;

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
 * Access token → Authentication: roles[] → ROLE_<code>. Tên authentication = sub (userId) — GatewayRequestHeaders gắn
 * X-User-Id / X-User-Roles từ token này (chỉ để log). Gateway không phân quyền theo role, service phía sau tự làm.
 */
@Component
public class JwtAuthorityConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        for (String role : readList(jwt, TokenClaims.ROLES)) {
            authorities.add(new SimpleGrantedAuthority(TokenClaims.ROLE_AUTHORITY_PREFIX + role));
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private List<String> readList(Jwt jwt, String claim) {
        List<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? List.of() : values;
    }
}
