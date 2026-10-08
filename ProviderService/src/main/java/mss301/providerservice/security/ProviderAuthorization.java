package mss301.providerservice.security;

import lombok.RequiredArgsConstructor;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
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
