package mss301.carecatalogservice.security;

import mss301.carecatalogservice.enums.RoleCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public class CurrentRolesArgumentResolver implements HandlerMethodArgumentResolver {

    private static final Set<String> KNOWN_ROLES = Arrays.stream(RoleCode.values())
            .map(RoleCode::name)
            .collect(Collectors.toUnmodifiableSet());

    // Nhận tham số controller có @CurrentRoles.
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentRoles.class)
                && Set.class.equals(parameter.getParameterType());
    }

    // Lấy danh sách role của người gọi từ Authentication.
    @Override
    public Set<RoleCode> resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                         NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Khách chưa đăng nhập trên các GET công khai: không có role (xem như khách hàng). Endpoint khác vẫn bị
        // @PreAuthorize chặn trước khi tới đây.
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return EnumSet.noneOf(RoleCode.class);
        }
        Set<RoleCode> roles = EnumSet.noneOf(RoleCode.class);
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if (name == null || !name.startsWith(TokenClaims.ROLE_AUTHORITY_PREFIX)) {
                continue;
            }
            String roleCode = name.substring(TokenClaims.ROLE_AUTHORITY_PREFIX.length());
            if (KNOWN_ROLES.contains(roleCode)) {
                roles.add(RoleCode.valueOf(roleCode));
            }
        }
        return roles;
    }
}
