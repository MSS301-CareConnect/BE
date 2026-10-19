package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.client.IdentityClient;
import mss301.providerservice.client.dto.IdentityUserResponse;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.service.IdentityAccountService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class IdentityAccountServiceImpl implements IdentityAccountService {

    IdentityClient identityClient;

    /** Gọi trước transaction ghi Provider DB. */
    @Override
    public IdentityUserResponse requireActiveRole(UUID userId, String requiredRole) {
        APIResponse<IdentityUserResponse> response = identityClient.get(userId);
        IdentityUserResponse user = response == null ? null : response.getResult();
        if (user == null) {
            throw new AppException(ErrorCode.IDENTITY_USER_NOT_FOUND);
        }
        boolean active = "ACTIVE".equals(user.getStatus());
        boolean hasRole = user.getRoles() != null && user.getRoles().contains(requiredRole);
        if (!active || !hasRole) {
            throw new AppException(ErrorCode.IDENTITY_USER_INVALID);
        }
        return user;
    }
}
