package mss301.providerservice.service;

import lombok.RequiredArgsConstructor;
import mss301.providerservice.client.IdentityClient;
import mss301.providerservice.client.dto.IdentityUserResponse;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.exception.*;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentityAccountService {
    private final IdentityClient identityClient;

    /** Gọi trước transaction ghi Provider DB. */
    public IdentityUserResponse requireActiveRole(UUID userId, String requiredRole) {
        APIResponse<IdentityUserResponse> response = identityClient.get(userId);
        IdentityUserResponse user = response == null ? null : response.getResult();
        if (user == null) throw new AppException(ErrorCode.IDENTITY_USER_NOT_FOUND);
        boolean active = "ACTIVE".equals(user.getStatus());
        boolean hasRole = user.getRoles() != null && user.getRoles().contains(requiredRole);
        if (!active || !hasRole) throw new AppException(ErrorCode.IDENTITY_USER_INVALID);
        return user;
    }
}
