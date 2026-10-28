package mss301.paymentservice.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class JwtAuthorityConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        read(jwt, TokenClaims.ROLES).forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        read(jwt, TokenClaims.PERMISSIONS).forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private List<String> read(Jwt jwt, String claim) {
        List<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? List.of() : values;
    }
}
