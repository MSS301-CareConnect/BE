package mss301.providerservice.security;

import lombok.RequiredArgsConstructor;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.enums.RoleCode;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProviderAuthorization {

    public boolean isAdmin() {
        return hasRole(RoleCode.ADMIN);
    }

    // Role trong access token hiện tại (ROLE_<code>) — dùng khi phạm vi dữ liệu khác nhau theo role trong cùng endpoint.
    public boolean hasRole(RoleCode roleCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String authority = TokenClaims.ROLE_AUTHORITY_PREFIX + roleCode.name();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

    public void requireCenterManagement(CareCenter center, UUID actorId) {
        if (!isAdmin() && !actorId.equals(center.getManagerUserId())) {
            throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
        }
    }

    public void requireCaregiverSelfOrCenterManagement(CaregiverProfile caregiver, UUID actorId) {
        if (actorId.equals(caregiver.getUserId()) || isAdmin()) {
            return;
        }
        requireCenterManagement(caregiver.getCenter(), actorId);
    }
}
